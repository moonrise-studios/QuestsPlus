package gg.moonrise.quests.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestStreakState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the cached daily quest state of each player: loads it from the database, hands it out, and drops it when
 * the quests are reset. Slot access, rerolls, progress and rewards live in the focused services next to it
 * ({@link QuestSlotService}, {@link QuestUsageService}, {@link QuestSelectionService}, {@link QuestProgressService},
 * {@link QuestRewardService} and {@link QuestTextService}).
 */
@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestService {

    private final QuestRepository questRepository;
    private final QuestStreakService streakService;
    private final QuestSlotService slotService;
    private final QuestRewardService rewardService;

    private final Cache<UUID, PlayerQuestState> stateCache = Caffeine.newBuilder().maximumSize(10_000).build();
    private final Cache<StateLoadKey, PendingStateLoad> stateLoadCache = Caffeine.newBuilder().maximumSize(10_000).build();

    public CompletableFuture<PlayerQuestState> ensurePlayerStateAsync(UUID playerId, String resetKey) {
        PlayerQuestState cached = stateCache.getIfPresent(playerId);
        if (cached != null && cached.resetKey().equals(resetKey)) {
            return CompletableFuture.completedFuture(cached);
        }

        StateLoadKey key = new StateLoadKey(playerId, resetKey);
        PendingStateLoad load = stateLoadCache.asMap().computeIfAbsent(key, ignored -> startStateLoad(playerId, resetKey));
        load.future().whenComplete((state, throwable) -> stateLoadCache.asMap().remove(key, load));
        return load.future().thenCompose(state -> load.invalidated().get()
                ? ensurePlayerStateAsync(playerId, resetKey)
                : CompletableFuture.completedFuture(state));
    }

    public CompletableFuture<PlayerQuestState> ensurePlayerStateAsync(Player player, String resetKey) {
        return ensurePlayerStateAsync(player.getUniqueId(), resetKey)
                .thenCompose(state -> rewardService.claimRetroactiveMilestones(player, state).thenApply(unused -> state))
                .thenCompose(state -> {
                    if (!streakService.isEnabled()) {
                        return CompletableFuture.completedFuture(state);
                    }
                    return streakService.ensureState(player, resetKey)
                            .thenCompose(streakState -> evaluateMissedStreakCompletion(player, state, streakState))
                            .thenApply(unused -> state);
                });
    }

    public Optional<PlayerQuestState> cachedState(UUID playerId, String resetKey) {
        return Optional.ofNullable(stateCache.getIfPresent(playerId))
                .filter(state -> state.resetKey().equals(resetKey));
    }

    /**
     * Drops the player's cached state and marks every load still in flight for them as stale, so a load that
     * read the database before the invalidation can neither re-cache its result nor be handed to new callers.
     */
    public void invalidateState(UUID playerId) {
        stateLoadCache.asMap().forEach((key, load) -> {
            if (key.playerId().equals(playerId)) {
                load.invalidated().set(true);
                stateLoadCache.asMap().remove(key, load);
            }
        });
        stateCache.invalidate(playerId);
    }

    public CompletableFuture<Void> resetDaily(UUID playerId, String resetKey) {
        invalidateState(playerId);
        // Invalidate again once the rows are gone: a load started between the first invalidation and the delete
        // may still have read the quests being reset.
        return questRepository.resetDailyQuests(playerId, resetKey)
                .thenRun(() -> invalidateState(playerId));
    }

    public void clearOnlineDailyStates() {
        Bukkit.getOnlinePlayers().forEach(player -> invalidateState(player.getUniqueId()));
    }

    public void claimRetroactiveMilestonesForOnlinePlayers() {
        if (!rewardService.questMilestonesEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerQuestState state = stateCache.getIfPresent(player.getUniqueId());
            if (state == null) {
                continue;
            }
            rewardService.claimRetroactiveMilestones(player, state);
        }
    }

    /**
     * Starts loading a player's state. The loaded state is only kept in {@link #stateCache} if the player's
     * state was not invalidated while the load was in flight (see {@link #invalidateState(UUID)}).
     */
    private PendingStateLoad startStateLoad(UUID playerId, String resetKey) {
        AtomicBoolean invalidated = new AtomicBoolean();
        CompletableFuture<PlayerQuestState> future = questRepository.loadQuests(playerId, resetKey)
                .thenCombine(questRepository.loadDifficultyCompletedCounts(playerId), LoadedState::new)
                .thenCombine(questRepository.loadExecutedMilestones(playerId), (loaded, executedMilestones) -> {
                    PlayerQuestState state = new PlayerQuestState(playerId, resetKey, normalizeQuestSlots(loaded.quests()), loaded.difficultyCompletions(), executedMilestones);
                    // Put first, then check: invalidateState marks the load before clearing the cache, so either the
                    // invalidation clears this entry or this check sees the mark and takes it back out.
                    stateCache.put(playerId, state);
                    if (invalidated.get()) {
                        stateCache.asMap().remove(playerId, state);
                    }
                    return state;
                });
        return new PendingStateLoad(future, invalidated);
    }

    /**
     * Awards the streak for the current reset window if the player finished it but the evaluation never ran,
     * which happens when the last quest completed while they were logging out. Evaluation is idempotent per reset
     * key and returns without a database round trip unless the window is actually complete.
     */
    private CompletableFuture<Void> evaluateMissedStreakCompletion(Player player, PlayerQuestState state, QuestStreakState streakState) {
        if (state.resetKey().equals(streakState.lastCompletedResetKey())) {
            return CompletableFuture.completedFuture(null);
        }
        return streakService.evaluateQuestCompletion(player, state, slotService.accessibleQuests(player, state))
                .<Void>thenApply(unused -> null)
                .exceptionally(throwable -> {
                    log.error("Failed to evaluate QuestsPlus streak for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    private List<GeneratedQuest> normalizeQuestSlots(List<GeneratedQuest> quests) {
        if (quests.isEmpty()) {
            return List.of();
        }

        List<GeneratedQuest> normalized = new ArrayList<>(quests.size());
        Set<Integer> usedSlots = new HashSet<>();
        for (GeneratedQuest quest : quests) {
            if (quest.slotIndex() >= 0) {
                usedSlots.add(quest.slotIndex());
            }
        }

        int fallbackSlot = 0;
        for (GeneratedQuest quest : quests) {
            if (quest.slotIndex() >= 0) {
                normalized.add(quest);
                continue;
            }
            while (usedSlots.contains(fallbackSlot)) {
                fallbackSlot++;
            }
            normalized.add(quest.withSlotIndex(fallbackSlot));
            usedSlots.add(fallbackSlot);
        }

        return normalized.stream()
                .sorted(Comparator.comparingInt(GeneratedQuest::slotIndex))
                .toList();
    }

    private record LoadedState(
            List<GeneratedQuest> quests,
            Map<String, Integer> difficultyCompletions
    ) {
    }

    private record StateLoadKey(UUID playerId, String resetKey) {
    }

    private record PendingStateLoad(CompletableFuture<PlayerQuestState> future, AtomicBoolean invalidated) {
    }
}
