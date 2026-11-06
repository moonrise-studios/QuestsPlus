package gg.moonrise.quests.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.event.QuestProgressEvent;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestCompletionStats;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestMilestone;
import gg.moonrise.quests.model.QuestMilestoneClaim;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.model.QuestSelectionResult;
import gg.moonrise.quests.model.QuestSelectionStatus;
import gg.moonrise.quests.model.QuestStreakState;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.util.QuestPlaceholders;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;
import gg.moonrise.quests.util.QuestTokens;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestService {

    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final QuestRepository questRepository;
    private final QuestStreakService streakService;
    private final GlobalQuestService globalQuestService;
    private final QuestProgressIndicatorService progressIndicatorService;

    private final Cache<UUID, PlayerQuestState> stateCache = Caffeine.newBuilder().maximumSize(10_000).build();
    private final Cache<StateLoadKey, PendingStateLoad> stateLoadCache = Caffeine.newBuilder().maximumSize(10_000).build();
    private final Cache<RerollUsageKey, Integer> rerollUsageCache = Caffeine.newBuilder().maximumSize(10_000).build();
    private final Cache<ResetPurchaseUsageKey, Integer> resetPurchaseUsageCache = Caffeine.newBuilder().maximumSize(10_000).build();
    private final AtomicReference<ConfigDerived> configDerivedCache = new AtomicReference<>();

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
     * Drops the player's cached state and marks every load still in flight for them as stale, so a load that
     * read the database before the invalidation can neither re-cache its result nor be handed to new callers.
     */
    private void invalidateState(UUID playerId) {
        stateLoadCache.asMap().forEach((key, load) -> {
            if (key.playerId().equals(playerId)) {
                load.invalidated().set(true);
                stateLoadCache.asMap().remove(key, load);
            }
        });
        stateCache.invalidate(playerId);
    }

    public CompletableFuture<PlayerQuestState> ensurePlayerStateAsync(Player player, String resetKey) {
        return ensurePlayerStateAsync(player.getUniqueId(), resetKey)
                .thenCompose(state -> claimRetroactiveMilestones(player, state).thenApply(unused -> state))
                .thenCompose(state -> {
                    if (!streakService.isEnabled()) {
                        return CompletableFuture.completedFuture(state);
                    }
                    return streakService.ensureState(player, resetKey)
                            .thenCompose(streakState -> evaluateMissedStreakCompletion(player, state, streakState))
                            .thenApply(unused -> state);
                });
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
        return streakService.evaluateQuestCompletion(player, state, accessibleQuests(player, state))
                .<Void>thenApply(unused -> null)
                .exceptionally(throwable -> {
                    log.error("Failed to evaluate QuestsPlus streak for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    public PlayerQuestState cachedState(UUID playerId, String resetKey) {
        PlayerQuestState state = stateCache.getIfPresent(playerId);
        return state != null && state.resetKey().equals(resetKey) ? state : null;
    }

    public boolean hasCachedGlobalQuest(QuestType type) {
        GlobalQuestState state = globalQuestService.cachedActiveState();
        return state != null && !state.quest().completed() && state.quest().type().equals(type);
    }

    public CompletableFuture<Void> resetDaily(UUID playerId, String resetKey) {
        invalidateState(playerId);
        // Invalidate again once the rows are gone: a load started between the first invalidation and the delete
        // may still have read the quests being reset.
        return questRepository.resetDailyQuests(playerId, resetKey)
                .thenRun(() -> invalidateState(playerId));
    }

    public CompletableFuture<Boolean> recordQuestResetPurchaseAndReset(UUID playerId, String resetKey) {
        int limit = questResetDailyLimit();
        if (limit <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        return questRepository.recordQuestResetPurchaseAndReset(playerId, resetKey, limit)
                .thenApply(updatedUsed -> {
                    if (updatedUsed < 0) {
                        return false;
                    }
                    resetPurchaseUsageCache.put(new ResetPurchaseUsageKey(playerId, resetKey), updatedUsed);
                    invalidateState(playerId);
                    return true;
                });
    }

    public CompletableFuture<Integer> completeDailyQuests(Player player, String resetKey) {
        return ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> runOnEntity(player, () -> {
                    List<CompletableFuture<Boolean>> updates = new ArrayList<>();
                    for (GeneratedQuest quest : state.quests()) {
                        if (quest.completed() || !canAccessQuest(player, quest)) {
                            continue;
                        }
                        // Re-checked atomically: progress from another thread may have completed it meanwhile.
                        state.updateQuest(
                                quest.instanceId(),
                                current -> current.completed() ? Optional.empty() : Optional.of(current.withProgress(current.goalAmount(), true)),
                                change -> updates.add(persistProgress(player, state, change.previous(), change.updated(), "admin completion"))
                        ).ifPresent(change -> progressIndicatorService.showPersonal(player, change.updated(), change.previous().progress()));
                    }

                    if (updates.isEmpty()) {
                        return CompletableFuture.completedFuture(0);
                    }
                    return CompletableFuture.allOf(updates.toArray(CompletableFuture[]::new))
                            .thenApply(unused -> (int) updates.stream().filter(CompletableFuture::join).count());
                }));
    }

    public CompletableFuture<Void> resetRerolls(UUID playerId, String resetKey) {
        rerollUsageCache.invalidate(new RerollUsageKey(playerId, resetKey));
        return questRepository.resetRerolls(playerId, resetKey);
    }

    /**
     * Returns the quest slot layout of the active config.
     */
    public QuestMenuLayout menuLayout() {
        return configDerived().layout();
    }

    /**
     * Returns the values derived from the active config. They are computed once per config instance; a reload
     * swaps the instance in {@link ConfigProvider}, which makes the next call compute them again.
     */
    private ConfigDerived configDerived() {
        Config config = configProvider.get();
        ConfigDerived cached = configDerivedCache.get();
        if (cached != null && cached.config() == config) {
            return cached;
        }
        ConfigDerived derived = new ConfigDerived(config, QuestMenuLayout.of(config), PermissionLimits.premiumSlots(config), PermissionLimits.rerolls(config));
        configDerivedCache.set(derived);
        return derived;
    }

    public int dailyQuestSlots() {
        return menuLayout().dailyQuestSlots();
    }

    public int totalQuestSlots(Player player) {
        ConfigDerived derived = configDerived();
        QuestMenuLayout layout = derived.layout();
        return Math.min(layout.capacity(), layout.dailyQuestSlots() + premiumSlotLimit(player, derived));
    }

    public int totalVisibleQuestSlots() {
        return menuLayout().totalVisibleQuestSlots();
    }

    public int visiblePremiumSlotLimit() {
        return menuLayout().visiblePremiumSlots();
    }

    public int premiumSlotLimit(Player player) {
        return premiumSlotLimit(player, configDerived());
    }

    private int premiumSlotLimit(Player player, ConfigDerived derived) {
        QuestMenuLayout layout = derived.layout();
        return Math.clamp(derived.premiumLimits().limitFor(player), 0, Math.max(0, layout.capacity() - layout.dailyQuestSlots()));
    }

    public boolean isPremiumSlot(int slotIndex) {
        return menuLayout().isPremiumSlot(slotIndex);
    }

    public boolean canAccessSlot(Player player, int slotIndex) {
        ConfigDerived derived = configDerived();
        QuestMenuLayout layout = derived.layout();
        if (slotIndex < 0 || slotIndex >= layout.totalVisibleQuestSlots()) {
            return false;
        }
        if (!layout.isPremiumSlot(slotIndex)) {
            return true;
        }
        return slotIndex < layout.dailyQuestSlots() + premiumSlotLimit(player, derived);
    }

    public boolean canAccessQuest(Player player, GeneratedQuest quest) {
        if (!quest.premium()) {
            return true;
        }
        return canAccessSlot(player, quest.slotIndex());
    }

    public List<GeneratedQuest> accessibleQuests(Player player, PlayerQuestState state) {
        return state.quests().stream()
                .filter(quest -> canAccessQuest(player, quest))
                .toList();
    }

    public QuestResetEligibility resetEligibility(Player player, PlayerQuestState state) {
        int required = totalQuestSlots(player);
        int completed = 0;
        for (int slotIndex = 0; slotIndex < required; slotIndex++) {
            GeneratedQuest quest = state.questAtSlot(slotIndex);
            if (quest != null && quest.completed() && canAccessQuest(player, quest)) {
                completed++;
            }
        }
        return new QuestResetEligibility(completed, required);
    }

    public CompletableFuture<Integer> ensureRerollUsageAsync(UUID playerId, String resetKey) {
        RerollUsageKey key = new RerollUsageKey(playerId, resetKey);
        Integer cached = rerollUsageCache.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return questRepository.loadRerollsUsed(playerId, resetKey)
                .thenApply(used -> {
                    rerollUsageCache.put(key, used);
                    return used;
                });
    }

    public int cachedRerollsUsed(UUID playerId, String resetKey) {
        Integer used = rerollUsageCache.getIfPresent(new RerollUsageKey(playerId, resetKey));
        return used == null ? 0 : used;
    }

    public CompletableFuture<Integer> ensureQuestResetPurchaseUsageAsync(UUID playerId, String resetKey) {
        ResetPurchaseUsageKey key = new ResetPurchaseUsageKey(playerId, resetKey);
        Integer cached = resetPurchaseUsageCache.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return refreshQuestResetPurchaseUsageAsync(playerId, resetKey);
    }

    public CompletableFuture<Integer> refreshQuestResetPurchaseUsageAsync(UUID playerId, String resetKey) {
        ResetPurchaseUsageKey key = new ResetPurchaseUsageKey(playerId, resetKey);
        return questRepository.loadQuestResetsUsed(playerId, resetKey)
                .thenApply(used -> {
                    resetPurchaseUsageCache.put(key, used);
                    return used;
                });
    }

    public int cachedQuestResetPurchasesUsed(UUID playerId, String resetKey) {
        Integer used = resetPurchaseUsageCache.getIfPresent(new ResetPurchaseUsageKey(playerId, resetKey));
        return used == null ? 0 : used;
    }

    public int questResetDailyLimit() {
        Config.QuestResetMenu menu = configProvider.get().getMenu().getResetMenu();
        return Math.max(0, menu == null ? new Config.QuestResetMenu().getDailyLimit() : menu.getDailyLimit());
    }

    public int questResetPurchasesRemaining(UUID playerId, String resetKey) {
        return Math.max(0, questResetDailyLimit() - cachedQuestResetPurchasesUsed(playerId, resetKey));
    }

    public int rerollLimit(Player player) {
        return configDerived().rerollLimits().limitFor(player);
    }

    public int rerollsRemaining(Player player, String resetKey) {
        int limit = rerollLimit(player);
        int used = cachedRerollsUsed(player.getUniqueId(), resetKey);
        return Math.max(0, limit - used);
    }

    public CompletableFuture<QuestSelectionResult> selectQuestDifficulty(Player player, String resetKey, int slotIndex, String difficultyId) {
        String normalizedDifficulty = QuestNames.normalize(difficultyId);
        UUID playerId = player.getUniqueId();
        int rerollLimit = rerollLimit(player);
        if (!canAccessSlot(player, slotIndex) || normalizedDifficulty.isBlank()) {
            return ensurePlayerStateAsync(player, resetKey).thenApply(state -> new QuestSelectionResult(state, QuestSelectionStatus.INVALID, cachedRerollsUsed(playerId, resetKey), rerollLimit));
        }

        return ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> {
                    if (state.hasQuestAtSlot(slotIndex)) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.SLOT_FILLED, cachedRerollsUsed(playerId, resetKey), rerollLimit));
                    }
                    QuestDifficulty difficulty = definitionService.difficulty(normalizedDifficulty);
                    if (difficulty != null && !difficulty.requirementsMet(state)) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.REQUIREMENTS_NOT_MET, cachedRerollsUsed(playerId, resetKey), rerollLimit));
                    }

                    GeneratedQuest generated = generateQuestForDifficultySlot(playerId, resetKey, slotIndex, normalizedDifficulty, state, null);
                    if (generated == null) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_AVAILABLE_QUEST, cachedRerollsUsed(playerId, resetKey), rerollLimit));
                    }
                    // A concurrent selection may have filled the slot while this quest was generated.
                    if (!state.putQuestIfSlotEmpty(generated)) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.SLOT_FILLED, cachedRerollsUsed(playerId, resetKey), rerollLimit));
                    }

                    return questRepository.saveGeneratedQuests(List.of(generated))
                            .whenComplete((unused, throwable) -> {
                                if (throwable == null) {
                                    return;
                                }
                                // The quest was never stored, so take it back out of the cached state; otherwise the
                                // slot would look filled and its progress updates would hit no row.
                                state.removeQuest(generated.instanceId());
                            })
                            .thenApply(unused -> new QuestSelectionResult(state, QuestSelectionStatus.SELECTED, cachedRerollsUsed(playerId, resetKey), rerollLimit));
                });
    }

    public CompletableFuture<QuestSelectionResult> rerollQuestDifficulty(Player player, String resetKey, int slotIndex, String difficultyId) {
        String normalizedDifficulty = QuestNames.normalize(difficultyId);
        UUID playerId = player.getUniqueId();
        int limit = rerollLimit(player);
        if (!canAccessSlot(player, slotIndex) || normalizedDifficulty.isBlank()) {
            return ensurePlayerStateAsync(player, resetKey).thenApply(state -> new QuestSelectionResult(state, QuestSelectionStatus.INVALID, cachedRerollsUsed(playerId, resetKey), limit));
        }

        return ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> ensureRerollUsageAsync(playerId, resetKey).thenCompose(used -> {
                    GeneratedQuest existing = state.questAtSlot(slotIndex);
                    if (existing == null) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.INVALID, used, limit));
                    }
                    if (existing.completed()) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.COMPLETED, used, limit));
                    }
                    QuestDifficulty difficulty = definitionService.difficulty(normalizedDifficulty);
                    if (difficulty != null && !difficulty.requirementsMet(state)) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.REQUIREMENTS_NOT_MET, used, limit));
                    }
                    if (used >= limit) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_REROLLS, used, limit));
                    }

                    GeneratedQuest generated = generateQuestForDifficultySlot(playerId, resetKey, slotIndex, normalizedDifficulty, state, existing);
                    if (generated == null) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_AVAILABLE_QUEST, used, limit));
                    }

                    // The cached checks above can be stale (e.g. a double click submits two rerolls before either
                    // finishes), so the repository re-checks the limit and the old row inside its transaction.
                    return questRepository.replaceQuestAndIncrementRerolls(existing.instanceId(), generated, limit)
                            .thenCompose(outcome -> applyRerollOutcome(player, resetKey, state, slotIndex, existing, generated, limit, outcome));
                }));
    }

    private CompletableFuture<QuestSelectionResult> applyRerollOutcome(Player player, String resetKey, PlayerQuestState state, int slotIndex,
                                                                       GeneratedQuest existing, GeneratedQuest generated, int limit,
                                                                       QuestRepository.RerollOutcome outcome) {
        RerollUsageKey usageKey = new RerollUsageKey(player.getUniqueId(), resetKey);
        return switch (outcome) {
            case QuestRepository.RerollOutcome.Rerolled rerolled -> {
                state.replaceQuestAtSlot(slotIndex, generated);
                rerollUsageCache.put(usageKey, rerolled.used());
                yield CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.REROLLED, rerolled.used(), limit));
            }
            case QuestRepository.RerollOutcome.LimitReached limitReached -> {
                rerollUsageCache.put(usageKey, limitReached.used());
                yield CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_REROLLS, limitReached.used(), limit));
            }
            case QuestRepository.RerollOutcome.QuestMissing missing -> {
                int used = cachedRerollsUsed(player.getUniqueId(), resetKey);
                GeneratedQuest current = state.questAtSlot(slotIndex);
                boolean stale = current != null && current.instanceId().equals(existing.instanceId());
                if (!stale) {
                    // Another request already replaced this quest; the cached state shows its result.
                    yield CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.INVALID, used, limit));
                }
                // The row disappeared behind the cache's back, so reload before showing the menu again.
                invalidateState(player.getUniqueId());
                yield ensurePlayerStateAsync(player, resetKey)
                        .thenApply(fresh -> new QuestSelectionResult(fresh, QuestSelectionStatus.INVALID, used, limit));
            }
        };
    }

    public void clearOnlineDailyStates() {
        Bukkit.getOnlinePlayers().forEach(player -> invalidateState(player.getUniqueId()));
    }

    public void claimRetroactiveMilestonesForOnlinePlayers() {
        if (!questMilestonesEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerQuestState state = stateCache.getIfPresent(player.getUniqueId());
            if (state == null) {
                continue;
            }
            claimRetroactiveMilestones(player, state);
        }
    }

    public List<QuestProgressResult> progressMatching(Player player, QuestType type, String resetKey, Predicate<GeneratedQuest> matcher) {
        return progressMatching(player, type, resetKey, 1, matcher);
    }

    public List<QuestProgressResult> progressMatching(Player player, QuestType type, String resetKey, int amount, Predicate<GeneratedQuest> matcher) {
        return progressMatching(player, type, resetKey, amount, matcher, QuestProgressEvent.Cause.EVENT);
    }

    public List<QuestProgressResult> progressMatching(Player player, QuestType type, String resetKey, int amount, Predicate<GeneratedQuest> matcher, QuestProgressEvent.Cause cause) {
        if (amount <= 0) {
            return List.of();
        }

        PlayerQuestState state = cachedState(player.getUniqueId(), resetKey);
        if (state == null) {
            ensurePlayerStateAsync(player, resetKey)
                    .thenRun(() -> Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            progressMatching(player, type, resetKey, amount, matcher, cause);
                        }
                    }))
                    .exceptionally(throwable -> {
                        log.error("Failed to load QuestsPlus player state before applying {} progress for {}.", type.key(), player.getUniqueId(), throwable);
                        return null;
                    });
            return List.of();
        }

        globalQuestService.progressMatching(player, type, amount, matcher, cause);

        return applyProgress(player, state, quest -> quest.type().equals(type) && matcher.test(quest), amount, cause, "progress");
    }

    public List<QuestProgressResult> progressAdminGoal(Player player, String resetKey, int amount, String definitionId) {
        if (amount <= 0) {
            return List.of();
        }

        String normalizedDefinitionId = QuestNames.normalize(definitionId);
        if (normalizedDefinitionId.isBlank()) {
            return List.of();
        }

        PlayerQuestState state = cachedState(player.getUniqueId(), resetKey);
        if (state == null) {
            return List.of();
        }

        return applyProgress(player, state, quest -> quest.definitionId().equals(normalizedDefinitionId), amount, QuestProgressEvent.Cause.COMMAND, "command progress");
    }

    public List<QuestProgressResult> progressAdminGoal(Player player, String resetKey, int amount, QuestType type) {
        if (amount <= 0 || type == null) {
            return List.of();
        }

        PlayerQuestState state = cachedState(player.getUniqueId(), resetKey);
        if (state == null) {
            return List.of();
        }

        return applyProgress(player, state, quest -> quest.type().equals(type), amount, QuestProgressEvent.Cause.COMMAND, "command progress");
    }

    /**
     * Credits {@code amount} to every active, accessible quest matching {@code filter}. The QuestProgressEvent is
     * fired outside any state update, because third-party listeners may call back into QuestsPlus from other
     * threads; each approved amount is then applied atomically only if the same quest instance is still active,
     * clamped against its current progress. The database write is queued inside that atomic update so writes for
     * one quest reach the database in the order the cached progress changed.
     */
    private List<QuestProgressResult> applyProgress(Player player, PlayerQuestState state, Predicate<GeneratedQuest> filter, int amount,
                                                    QuestProgressEvent.Cause cause, String context) {
        List<GeneratedQuest> candidates = state.quests().stream()
                .filter(quest -> !quest.completed() && canAccessQuest(player, quest) && filter.test(quest))
                .toList();

        List<QuestProgressResult> results = new ArrayList<>();
        for (GeneratedQuest candidate : candidates) {
            int approvedAmount = approveProgress(player, candidate, amount, cause);
            if (approvedAmount <= 0) {
                continue;
            }

            // The quest may have been rerolled, reset, completed or advanced while listeners ran.
            state.updateQuest(
                    candidate.instanceId(),
                    current -> current.completed() ? Optional.empty() : Optional.of(withAddedProgress(current, approvedAmount)),
                    change -> persistProgress(player, state, change.previous(), change.updated(), context)
            ).ifPresent(change -> {
                results.add(new QuestProgressResult(change.updated(), change.updated().completed()));
                progressIndicatorService.showPersonal(player, change.updated(), change.previous().progress());
            });
        }
        return results;
    }

    private GeneratedQuest withAddedProgress(GeneratedQuest quest, int amount) {
        int updatedProgress = clampedProgress(quest.progress(), amount, quest.goalAmount());
        return quest.withProgress(updatedProgress, updatedProgress >= quest.goalAmount());
    }

    private CompletableFuture<Boolean> persistProgress(Player player, PlayerQuestState state, GeneratedQuest previous, GeneratedQuest updated, String context) {
        return questRepository.updateProgress(updated)
                .thenApply(applied -> {
                    if (!applied) {
                        state.replaceQuestIfPresent(previous);
                        log.warn("Skipped QuestsPlus {} side effects for quest {} because the progress row was not updated.", context, updated.instanceId());
                        return false;
                    }
                    if (updated.completed() && !previous.completed()) {
                        try {
                            completeQuest(player, state, updated);
                        } catch (RuntimeException exception) {
                            // The row is stored, so a failing side effect must not roll the cached quest back.
                            log.error("Failed to run QuestsPlus completion side effects for quest {}.", updated.instanceId(), exception);
                        }
                    }
                    return true;
                })
                .exceptionally(throwable -> {
                    state.replaceQuestIfPresent(previous);
                    log.error("Failed to persist QuestsPlus {} for quest {}.", context, updated.instanceId(), throwable);
                    return false;
                });
    }

    private <T> CompletableFuture<T> runOnEntity(Player player, Supplier<CompletableFuture<T>> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            Scheduler.entity(player).run(task -> {
                try {
                    supplier.get().whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            future.complete(result);
                        } else {
                            future.completeExceptionally(throwable);
                        }
                    });
                } catch (RuntimeException exception) {
                    future.completeExceptionally(exception);
                }
            }, () -> future.completeExceptionally(new IllegalStateException("Player is not available")));
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    private int clampedProgress(int progress, int amount, int goalAmount) {
        return Math.clamp((long) progress + amount, 0, Math.max(0, goalAmount));
    }

    private int approveProgress(Player player, GeneratedQuest quest, int amount, QuestProgressEvent.Cause cause) {
        QuestDefinition definition = definitionService.definition(quest).orElse(null);
        if (definition == null) {
            log.warn("Skipping QuestsPlus progress for generated quest {} because definition '{}' is not loaded.", quest.instanceId(), quest.definitionId());
            return 0;
        }

        QuestProgressEvent event = new QuestProgressEvent(player, definition, quest, cause == null ? QuestProgressEvent.Cause.UNKNOWN : cause, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }
        return event.amount();
    }

    public void sendProgress(CommandSender sender, PlayerQuestState state) {
        configProvider.get().getMessages().getProgressHeader().send(sender, Placeholder.unparsed("reset_key", state.resetKey()));
        for (GeneratedQuest quest : state.quests()) {
            if (sender instanceof Player player && !canAccessQuest(player, quest)) {
                continue;
            }
            configProvider.get().getMessages().getProgressLine().send(
                    sender,
                    Placeholder.unparsed("quest_display_name", QuestTokens.stripTags(quest.displayName())),
                    Placeholder.unparsed("progress", QuestNumberFormatter.format(quest.progress())),
                    Placeholder.unparsed("goal_amount", QuestNumberFormatter.format(quest.goalAmount())),
                    Placeholder.unparsed("status", quest.completed() ? "completed" : "active")
            );
        }
    }

    public void sendCompletedCount(CommandSender sender, PlayerQuestState state) {
        configProvider.get().getMessages().getCompletedCount().send(sender, Placeholder.unparsed("quests_completed", QuestNumberFormatter.format(state.questsCompleted())));
        Map<String, Integer> remainingCounts = new LinkedHashMap<>(state.difficultyCompletions());
        for (QuestDifficulty difficulty : definitionService.difficulties()) {
            Integer count = remainingCounts.remove(difficulty.id());
            int completed = count == null ? 0 : count;
            sendCompletedDifficultyLine(sender, difficulty.id(), difficulty.displayName(), completed);
        }
        for (Map.Entry<String, Integer> entry : remainingCounts.entrySet()) {
            sendCompletedDifficultyLine(sender, entry.getKey(), entry.getKey(), entry.getValue());
        }
    }

    public String replaceQuestTokens(String template, GeneratedQuest quest, Player player) {
        Map<String, String> values = commonPlaceholders(quest, player);
        values.putAll(definitionService.variablePlaceholders(quest));
        QuestTokens.putQuestNumbers(values, quest, true);
        return QuestTokens.apply(template, values);
    }

    public String replaceRawVariableDisplayValues(String template, GeneratedQuest quest) {
        String output = template == null ? "" : template;
        Map<String, String> displayValues = definitionService.variablePlaceholders(quest);
        for (Map.Entry<String, String> entry : quest.variables().entrySet()) {
            String rawValue = entry.getValue();
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            String displayValue = displayValues.get(entry.getKey());
            if (displayValue == null) {
                displayValue = displayValues.get(QuestPlaceholders.placeholderKey(entry.getKey()));
            }
            if (displayValue == null || displayValue.equals(rawValue)) {
                continue;
            }
            output = output.replace(rawValue, displayValue);
        }
        return output;
    }

    private GeneratedQuest generateQuestForDifficultySlot(UUID playerId, String resetKey, int slotIndex, String difficultyId, PlayerQuestState state, GeneratedQuest replacing) {
        List<QuestDefinition> pool = definitionService.enabledDefinitions().stream()
                .filter(definition -> definition.difficultyId().equals(difficultyId))
                .toList();
        if (pool.isEmpty()) {
            return null;
        }

        Set<String> activeDefinitionIds = state.quests().stream()
                .filter(quest -> replacing == null || !quest.instanceId().equals(replacing.instanceId()))
                .map(GeneratedQuest::definitionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<QuestDefinition> unused = pool.stream()
                .filter(definition -> !activeDefinitionIds.contains(definition.id()))
                .toList();
        List<QuestDefinition> candidates = unused.isEmpty() ? pool : unused;
        if (replacing != null && candidates.size() > 1) {
            List<QuestDefinition> withoutCurrent = candidates.stream()
                    .filter(definition -> !definition.id().equals(replacing.definitionId()))
                    .toList();
            if (!withoutCurrent.isEmpty()) {
                candidates = withoutCurrent;
            }
        }
        QuestDefinition definition = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Map<String, String> variables = definitionService.resolveVariables(definition);
        return definitionService.handler(definition.type())
                .createGeneratedQuest(definition, playerId, resetKey, variables)
                .withSlotIndex(slotIndex)
                .withPremium(isPremiumSlot(slotIndex));
    }

    /**
     * Runs the completion pipeline for a quest whose progress row is already stored as completed. Because the
     * completion is committed, the stats, milestones and reward commands must happen even if the player has
     * logged out meanwhile; only the chat message and the streak evaluation need them online. A streak missed
     * that way is evaluated on the player's next join (see {@link #ensurePlayerStateAsync(Player, String)}).
     */
    private void completeQuest(Player player, PlayerQuestState state, GeneratedQuest quest) {
        recordCompletionStats(player, state, quest);
        runOnEntityOrRetired(player, () -> {
            if (player.isOnline()) {
                configProvider.get().getMessages().getQuestCompleted().send(player, Placeholder.unparsed("quest_display_name", QuestTokens.stripTags(quest.displayName())));
            }
            // Already on the player's entity thread, so the commands run directly.
            dispatchConsoleCommands(player.getUniqueId(), resolvedRewardCommands(player, quest, canAccessQuest(player, quest)));
            evaluateStreak(player, state);
        }, () -> {
            // Progress is only credited to quests the player can access, so a premium quest that got completed
            // was accessible; the permission is not re-checked because it may no longer be readable offline.
            dispatchCommittedRewardCommands(player, resolvedRewardCommands(player, quest, quest.premium()));
        });
    }

    private List<String> resolvedRewardCommands(Player player, GeneratedQuest quest, boolean premiumRewardsAccessible) {
        List<String> commands = new ArrayList<>();
        for (String command : rewardCommands(quest, premiumRewardsAccessible)) {
            commands.add(replaceQuestRewardTokens(command, quest, player));
        }
        return commands;
    }

    private void dispatchConsoleCommands(UUID playerId, List<String> commands) {
        for (String command : commands) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException exception) {
                log.error("Failed to run QuestsPlus reward command '{}' for {}.", command, playerId, exception);
            }
        }
    }

    private void evaluateStreak(Player player, PlayerQuestState state) {
        if (!streakService.isEnabled()) {
            return;
        }
        streakService.evaluateQuestCompletion(player, state, accessibleQuests(player, state))
                .exceptionally(throwable -> {
                    log.error("Failed to evaluate QuestsPlus streak for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    /**
     * Runs {@code task} on the player's entity thread, or {@code retired} if the player is gone before it can run.
     * Paper returns {@code null} without calling the retired callback when the entity is already removed.
     */
    private void runOnEntityOrRetired(Player player, Runnable task, Runnable retired) {
        ScheduledTask scheduled = Scheduler.entity(player).run(ignored -> task.run(), retired);
        if (scheduled == null) {
            retired.run();
        }
    }

    private void recordCompletionStats(Player player, PlayerQuestState state, GeneratedQuest quest) {
        QuestDifficulty difficulty = definitionService.difficulty(quest.difficultyId());
        List<QuestMilestone> milestones = !questMilestonesEnabled() || difficulty == null ? List.of() : difficulty.milestones();
        questRepository.incrementCompletionStats(player.getUniqueId(), quest.difficultyId(), milestones)
                .thenAccept(stats -> {
                    state.setDifficultyCompletions(quest.difficultyId(), stats.difficultyCompleted());
                    state.markMilestonesExecuted(stats.newlyExecutedMilestones().stream()
                            .map(milestone -> PlayerQuestState.milestoneKey(milestone.difficultyId(), milestone.completed()))
                            .toList());
                    handleMilestoneRewards(player, quest, stats);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to persist QuestsPlus completion stats for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    private CompletableFuture<Void> claimRetroactiveMilestones(Player player, PlayerQuestState state) {
        if (!questMilestonesEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        return questRepository.claimEligibleMilestones(player.getUniqueId(), state.difficultyCompletions(), definitionService.difficulties())
                .thenAccept(claims -> {
                    if (claims.isEmpty()) {
                        return;
                    }
                    state.markMilestonesExecuted(claims.stream()
                            .map(claim -> PlayerQuestState.milestoneKey(claim.difficultyId(), claim.milestone().completed()))
                            .toList());
                    handleMilestoneClaimRewards(player, claims);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to retroactively claim QuestsPlus milestones for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    private void sendCompletedDifficultyLine(CommandSender sender, String difficultyId, String difficultyDisplayName, int questsCompleted) {
        configProvider.get().getMessages().getCompletedDifficultyLine().send(
                sender,
                Placeholder.unparsed("quest_difficulty", QuestTokens.stripTags(difficultyDisplayName)),
                Placeholder.unparsed("difficulty_id", difficultyId),
                Placeholder.unparsed("quests_completed", QuestNumberFormatter.format(questsCompleted))
        );
    }

    private void handleMilestoneRewards(Player player, GeneratedQuest quest, QuestCompletionStats stats) {
        if (!questMilestonesEnabled() || stats.newlyExecutedMilestones().isEmpty()) {
            return;
        }

        List<String> commands = new ArrayList<>();
        for (QuestMilestone milestone : stats.newlyExecutedMilestones()) {
            for (String command : milestone.commands()) {
                commands.add(replaceMilestoneTokens(command, player, quest, stats, milestone));
            }
        }
        dispatchCommittedRewardCommands(player, commands);
        Scheduler.entity(player).run(task -> {
            if (!player.isOnline()) {
                return;
            }
            for (QuestMilestone milestone : stats.newlyExecutedMilestones()) {
                sendMilestoneCompletedMessage(player, quest, stats, milestone);
            }
        });
    }

    private void handleMilestoneClaimRewards(Player player, List<QuestMilestoneClaim> claims) {
        if (!questMilestonesEnabled() || claims.isEmpty()) {
            return;
        }

        List<String> commands = new ArrayList<>();
        for (QuestMilestoneClaim claim : claims) {
            for (String command : claim.milestone().commands()) {
                commands.add(replaceMilestoneClaimTokens(command, player, claim));
            }
        }
        dispatchCommittedRewardCommands(player, commands);
        Scheduler.entity(player).run(task -> {
            if (!player.isOnline()) {
                return;
            }
            for (QuestMilestoneClaim claim : claims) {
                sendMilestoneClaimedMessage(player, claim);
            }
        });
    }

    /**
     * Runs reward commands whose claim is already committed. They must run even if the player has logged out
     * meanwhile, so they go to the global scheduler as console commands; their tokens only need the name and
     * UUID, which the caller resolved while the Player was at hand.
     */
    private void dispatchCommittedRewardCommands(Player player, List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Scheduler.sync().run(task -> dispatchConsoleCommands(playerId, commands));
    }

    private void sendMilestoneCompletedMessage(Player player, GeneratedQuest quest, QuestCompletionStats stats, QuestMilestone milestone) {
        configProvider.get().getMessages().getMilestoneCompleted().send(
                player,
                Placeholder.unparsed("quest_display_name", QuestTokens.stripTags(quest.displayName())),
                Placeholder.unparsed("quest_difficulty", QuestTokens.stripTags(quest.difficultyDisplayName())),
                Placeholder.unparsed("difficulty", QuestTokens.stripTags(quest.difficultyDisplayName())),
                Placeholder.unparsed("difficulty_id", quest.difficultyId()),
                Placeholder.unparsed("completed", QuestNumberFormatter.format(stats.difficultyCompleted())),
                Placeholder.unparsed("milestone_completed", QuestNumberFormatter.format(milestone.completed())),
                Placeholder.unparsed("milestone_display_name", QuestTokens.stripTags(milestone.displayName()))
        );
    }

    private void sendMilestoneClaimedMessage(Player player, QuestMilestoneClaim claim) {
        configProvider.get().getMessages().getMilestoneClaimed().send(
                player,
                Placeholder.unparsed("quest_difficulty", QuestTokens.stripTags(claim.difficultyDisplayName())),
                Placeholder.unparsed("difficulty", QuestTokens.stripTags(claim.difficultyDisplayName())),
                Placeholder.unparsed("difficulty_id", claim.difficultyId()),
                Placeholder.unparsed("completed", QuestNumberFormatter.format(claim.difficultyCompleted())),
                Placeholder.unparsed("milestone_completed", QuestNumberFormatter.format(claim.milestone().completed())),
                Placeholder.unparsed("milestone_display_name", QuestTokens.stripTags(claim.milestone().displayName()))
        );
    }

    private String replaceMilestoneTokens(String template, Player player, GeneratedQuest quest, QuestCompletionStats stats, QuestMilestone milestone) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("difficulty", quest.difficultyDisplayName());
        values.put("difficulty_id", quest.difficultyId());
        values.put("completed", Integer.toString(stats.difficultyCompleted()));
        values.put("milestone_completed", Integer.toString(milestone.completed()));
        return QuestTokens.apply(template, values);
    }

    private String replaceMilestoneClaimTokens(String template, Player player, QuestMilestoneClaim claim) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("difficulty", claim.difficultyDisplayName());
        values.put("difficulty_id", claim.difficultyId());
        values.put("completed", Integer.toString(claim.difficultyCompleted()));
        values.put("milestone_completed", Integer.toString(claim.milestone().completed()));
        return QuestTokens.apply(template, values);
    }

    private List<String> rewardCommands(GeneratedQuest quest, boolean premiumRewardsAccessible) {
        List<String> commands = new ArrayList<>();
        QuestDifficulty difficulty = definitionService.difficulty(quest.difficultyId());
        if (difficulty != null) {
            randomDifficultyRewardCommand(difficulty.rewardCommands()).ifPresent(commands::add);
        }
        if (quest.premium() && premiumRewardsAccessible) {
            Config.PremiumQuestsFile premium = configProvider.get().getPremiumQuests();
            Map<String, Config.Rewards> rewards = premium == null || premium.getRewards() == null ? Map.of() : premium.getRewards();
            Config.Rewards bonus = rewards.get(quest.difficultyId());
            if (bonus == null) {
                bonus = rewards.get(QuestNames.normalize(quest.difficultyId()));
            }
            if (bonus != null && bonus.getCommands() != null) {
                randomDifficultyRewardCommand(bonus.getCommands()).ifPresent(commands::add);
            }
        }
        definitionService.definition(quest)
                .map(QuestDefinition::rewardCommands)
                .ifPresent(commands::addAll);
        return commands;
    }

    public boolean questMilestonesEnabled() {
        return configProvider.get().isQuestMilestonesEnabled();
    }

    private java.util.Optional<String> randomDifficultyRewardCommand(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(commands.get(ThreadLocalRandom.current().nextInt(commands.size())));
    }

    private Map<String, String> commonPlaceholders(GeneratedQuest quest, Player player) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("quest_id", quest.definitionId());
        values.put("quest_display_name", QuestTokens.stripTags(quest.displayName()));
        values.put("quest_difficulty", quest.difficultyDisplayName());
        values.put("quest_difficulty_id", quest.difficultyId());
        QuestTokens.putQuestNumbers(values, quest, true);
        return values;
    }

    private String replaceQuestRewardTokens(String template, GeneratedQuest quest, Player player) {
        Map<String, String> values = commonPlaceholders(quest, player);
        values.putAll(definitionService.variablePlaceholders(quest));
        QuestTokens.putQuestNumbers(values, quest, false);
        return QuestTokens.apply(template, values);
    }

    private List<GeneratedQuest> normalizeQuestSlots(List<GeneratedQuest> quests) {
        if (quests.isEmpty()) {
            return List.of();
        }

        List<GeneratedQuest> normalized = new ArrayList<>(quests.size());
        Set<Integer> usedSlots = new java.util.HashSet<>();
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

    private record RerollUsageKey(UUID playerId, String resetKey) {
    }

    private record ResetPurchaseUsageKey(UUID playerId, String resetKey) {
    }

    /**
     * Values computed from one config instance, which is compared by identity, not equality.
     */
    private record ConfigDerived(Config config, QuestMenuLayout layout, PermissionLimits premiumLimits, PermissionLimits rerollLimits) {
    }
}
