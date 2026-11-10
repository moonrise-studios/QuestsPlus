package gg.moonrise.quests.application.quest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.QuestResetMenu;
import gg.moonrise.quests.domain.model.QuestResetPurchaseOutcome;
import gg.moonrise.quests.infrastructure.persistence.QuestRepository;
import gg.moonrise.quests.core.service.ConfigProvider;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tracks how many rerolls and quest reset purchases each player used in a reset window, against their limits.
 */
@SpringComponent
@RequiredArgsConstructor
public class QuestUsageService {

    private final ConfigProvider configProvider;
    private final QuestRepository questRepository;
    private final QuestService questService;
    private final QuestSlotService slotService;

    private final Cache<RerollUsageKey, Integer> rerollUsageCache = Caffeine.newBuilder().maximumSize(10_000).build();
    private final Cache<ResetPurchaseUsageKey, Integer> resetPurchaseUsageCache = Caffeine.newBuilder().maximumSize(10_000).build();

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

    /**
     * Stores the reroll count the database reported after a reroll attempt.
     */
    public void recordRerollsUsed(UUID playerId, String resetKey, int used) {
        rerollUsageCache.put(new RerollUsageKey(playerId, resetKey), used);
    }

    public int rerollsRemaining(Player player, String resetKey) {
        int limit = slotService.rerollLimit(player);
        int used = cachedRerollsUsed(player.getUniqueId(), resetKey);
        return Math.max(0, limit - used);
    }

    public CompletableFuture<Void> resetRerolls(UUID playerId, String resetKey) {
        rerollUsageCache.invalidate(new RerollUsageKey(playerId, resetKey));
        return questRepository.resetRerolls(playerId, resetKey);
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
        QuestResetMenu menu = configProvider.get().getMenu().getResetMenu();
        return Math.max(0, menu == null ? new QuestResetMenu().getDailyLimit() : menu.getDailyLimit());
    }

    public int questResetPurchasesRemaining(UUID playerId, String resetKey) {
        return Math.max(0, questResetDailyLimit() - cachedQuestResetPurchasesUsed(playerId, resetKey));
    }

    public CompletableFuture<QuestResetPurchaseOutcome> recordQuestResetPurchaseAndReset(UUID playerId, String resetKey) {
        int limit = questResetDailyLimit();
        if (limit <= 0) {
            return CompletableFuture.completedFuture(new QuestResetPurchaseOutcome.LimitReached());
        }
        return questRepository.recordQuestResetPurchaseAndReset(playerId, resetKey, limit)
                .thenApply(outcome -> {
                    switch (outcome) {
                        case QuestResetPurchaseOutcome.Applied applied -> {
                            resetPurchaseUsageCache.put(new ResetPurchaseUsageKey(playerId, resetKey), applied.used());
                            questService.invalidateState(playerId);
                        }
                        case QuestResetPurchaseOutcome.LimitReached limitReached -> {
                            // Nothing was written, so the cached usage and quests stay valid.
                        }
                    }
                    return outcome;
                });
    }

    private record RerollUsageKey(UUID playerId, String resetKey) {
    }

    private record ResetPurchaseUsageKey(UUID playerId, String resetKey) {
    }
}
