package gg.moonrise.quests.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.QuestVariableSelector;
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
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

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
    private final Cache<RerollUsageKey, Integer> rerollUsageCache = Caffeine.newBuilder().maximumSize(10_000).build();
    private final Cache<ResetPurchaseUsageKey, Integer> resetPurchaseUsageCache = Caffeine.newBuilder().maximumSize(10_000).build();
    private final RandomGenerator random = RandomGenerator.getDefault();

    public CompletableFuture<PlayerQuestState> ensurePlayerStateAsync(UUID playerId, String resetKey) {
        PlayerQuestState cached = stateCache.getIfPresent(playerId);
        if (cached != null && cached.resetKey().equals(resetKey)) {
            return CompletableFuture.completedFuture(cached);
        }

        return questRepository.loadQuests(playerId, resetKey)
                .thenCombine(questRepository.loadDifficultyCompletedCounts(playerId), LoadedState::new)
                .thenCombine(questRepository.loadExecutedMilestones(playerId), (loaded, executedMilestones) -> {
                    PlayerQuestState state = new PlayerQuestState(playerId, resetKey, normalizeQuestSlots(loaded.quests()), loaded.difficultyCompletions(), executedMilestones);
                    stateCache.put(playerId, state);
                    return state;
                });
    }

    public CompletableFuture<PlayerQuestState> ensurePlayerStateAsync(Player player, String resetKey) {
        return ensurePlayerStateAsync(player.getUniqueId(), resetKey)
                .thenCompose(state -> claimRetroactiveMilestones(player, state).thenApply(unused -> state))
                .thenCompose(state -> streakService.ensureState(player, resetKey).thenApply(unused -> state));
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
        stateCache.invalidate(playerId);
        return questRepository.resetDailyQuests(playerId, resetKey);
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
                    stateCache.invalidate(playerId);
                    return true;
                });
    }

    public CompletableFuture<Integer> completeDailyQuests(Player player, String resetKey) {
        return ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> {
                    List<GeneratedQuest> completedQuests = new ArrayList<>();
                    synchronized (state) {
                        for (GeneratedQuest quest : state.quests()) {
                            if (quest.completed() || !canAccessQuest(player, quest)) {
                                continue;
                            }
                            GeneratedQuest completed = quest.withProgress(quest.goalAmount(), true);
                            state.replaceQuest(completed);
                            completedQuests.add(completed);
                            progressIndicatorService.showPersonal(player, completed, quest.progress());
                        }
                    }

                    if (completedQuests.isEmpty()) {
                        return CompletableFuture.completedFuture(0);
                    }

                    List<CompletableFuture<Void>> updates = new ArrayList<>(completedQuests.size());
                    for (GeneratedQuest quest : completedQuests) {
                        updates.add(questRepository.updateProgress(quest)
                                .exceptionally(throwable -> {
                                    log.error("Failed to persist QuestsPlus admin completion for quest {}.", quest.instanceId(), throwable);
                                    return null;
                                }));
                        completeQuest(player, state, quest);
                    }
                    return CompletableFuture.allOf(updates.toArray(CompletableFuture[]::new))
                            .thenApply(unused -> completedQuests.size());
                });
    }

    public CompletableFuture<PlayerQuestState> rerollDaily(UUID playerId, String resetKey) {
        return resetDaily(playerId, resetKey).thenCompose(unused -> ensurePlayerStateAsync(playerId, resetKey));
    }

    public CompletableFuture<PlayerQuestState> rerollDaily(Player player, String resetKey) {
        return resetDaily(player.getUniqueId(), resetKey).thenCompose(unused -> ensurePlayerStateAsync(player, resetKey));
    }

    public CompletableFuture<Void> resetRerolls(UUID playerId, String resetKey) {
        rerollUsageCache.invalidate(new RerollUsageKey(playerId, resetKey));
        return questRepository.resetRerolls(playerId, resetKey);
    }

    public int dailyQuestSlots() {
        return Math.max(1, configProvider.get().getDaily().getQuestCount());
    }

    public int totalQuestSlots(Player player) {
        return dailyQuestSlots() + premiumSlotLimit(player);
    }

    public int totalVisibleQuestSlots() {
        return dailyQuestSlots() + visiblePremiumSlotLimit();
    }

    public int visiblePremiumSlotLimit() {
        Config.PremiumQuestsFile premium = configProvider.get().getPremiumQuests();
        if (premium == null || !premium.isEnabled()) {
            return 0;
        }
        Map<String, Integer> limits = premium.getPermissionLimits() == null ? Map.of() : premium.getPermissionLimits();
        int highest = 0;
        for (Integer configuredLimit : limits.values()) {
            int limit = configuredLimit == null ? 0 : Math.max(0, configuredLimit);
            if (limit > highest) {
                highest = limit;
            }
        }
        return highest;
    }

    public int premiumSlotLimit(Player player) {
        Config.PremiumQuestsFile premium = configProvider.get().getPremiumQuests();
        if (premium == null || !premium.isEnabled()) {
            return 0;
        }
        Map<String, Integer> limits = premium.getPermissionLimits() == null ? Map.of() : premium.getPermissionLimits();
        int highest = 0;
        for (Map.Entry<String, Integer> entry : limits.entrySet()) {
            String key = QuestNames.normalize(entry.getKey());
            int limit = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            if (key.isBlank() || limit <= highest) {
                continue;
            }
            if (player.hasPermission("questsplus.premium." + key)) {
                highest = limit;
            }
        }
        return highest;
    }

    public boolean isPremiumSlot(int slotIndex) {
        return slotIndex >= dailyQuestSlots();
    }

    public boolean canAccessSlot(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= totalVisibleQuestSlots()) {
            return false;
        }
        if (!isPremiumSlot(slotIndex)) {
            return true;
        }
        int firstPremiumSlot = dailyQuestSlots();
        return slotIndex < firstPremiumSlot + premiumSlotLimit(player);
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
        Config.Rerolls rerolls = configProvider.get().getDaily().getRerolls();
        Map<String, Integer> limits = rerolls == null ? Map.of() : rerolls.getPermissionLimits();
        if (limits == null || limits.isEmpty()) {
            return 0;
        }

        int highest = 0;
        for (Map.Entry<String, Integer> entry : limits.entrySet()) {
            String key = QuestNames.normalize(entry.getKey());
            int limit = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            if (key.isBlank() || limit <= highest) {
                continue;
            }
            if (player.hasPermission("questsplus.reroll." + key)) {
                highest = limit;
            }
        }
        return highest;
    }

    public int rerollsRemaining(Player player, String resetKey) {
        int limit = rerollLimit(player);
        int used = cachedRerollsUsed(player.getUniqueId(), resetKey);
        return Math.max(0, limit - used);
    }

    public CompletableFuture<QuestSelectionResult> selectQuestDifficulty(Player player, String resetKey, int slotIndex, String difficultyId) {
        String normalizedDifficulty = QuestNames.normalize(difficultyId);
        if (!canAccessSlot(player, slotIndex) || normalizedDifficulty.isBlank()) {
            return ensurePlayerStateAsync(player, resetKey).thenApply(state -> new QuestSelectionResult(state, QuestSelectionStatus.INVALID, cachedRerollsUsed(player.getUniqueId(), resetKey), rerollLimit(player)));
        }

        return ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> {
                    GeneratedQuest generated;
                    synchronized (state) {
                        if (state.hasQuestAtSlot(slotIndex)) {
                            return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.SLOT_FILLED, cachedRerollsUsed(player.getUniqueId(), resetKey), rerollLimit(player)));
                        }
                        QuestDifficulty difficulty = definitionService.difficulty(normalizedDifficulty);
                        if (difficulty != null && !difficulty.requirementsMet(state)) {
                            return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.REQUIREMENTS_NOT_MET, cachedRerollsUsed(player.getUniqueId(), resetKey), rerollLimit(player)));
                        }

                        generated = generateQuestForDifficultySlot(player.getUniqueId(), resetKey, slotIndex, normalizedDifficulty, state, null);
                        if (generated == null) {
                            return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_AVAILABLE_QUEST, cachedRerollsUsed(player.getUniqueId(), resetKey), rerollLimit(player)));
                        }
                        state.replaceQuest(generated);
                    }

                    return questRepository.saveGeneratedQuests(List.of(generated))
                            .thenApply(unused -> new QuestSelectionResult(state, QuestSelectionStatus.SELECTED, cachedRerollsUsed(player.getUniqueId(), resetKey), rerollLimit(player)));
                });
    }

    public CompletableFuture<QuestSelectionResult> rerollQuestDifficulty(Player player, String resetKey, int slotIndex, String difficultyId) {
        String normalizedDifficulty = QuestNames.normalize(difficultyId);
        if (!canAccessSlot(player, slotIndex) || normalizedDifficulty.isBlank()) {
            return ensurePlayerStateAsync(player, resetKey).thenApply(state -> new QuestSelectionResult(state, QuestSelectionStatus.INVALID, cachedRerollsUsed(player.getUniqueId(), resetKey), rerollLimit(player)));
        }

        return ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> ensureRerollUsageAsync(player.getUniqueId(), resetKey).thenCompose(used -> {
                    int limit = rerollLimit(player);
                    GeneratedQuest existing;
                    GeneratedQuest generated;
                    synchronized (state) {
                        existing = state.questAtSlot(slotIndex);
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

                        generated = generateQuestForDifficultySlot(player.getUniqueId(), resetKey, slotIndex, normalizedDifficulty, state, existing);
                        if (generated == null) {
                            return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_AVAILABLE_QUEST, used, limit));
                        }
                    }

                    return questRepository.replaceQuestAndIncrementRerolls(existing.instanceId(), generated)
                            .thenApply(updatedUsed -> {
                                synchronized (state) {
                                    state.replaceQuestAtSlot(slotIndex, generated);
                                }
                                rerollUsageCache.put(new RerollUsageKey(player.getUniqueId(), resetKey), updatedUsed);
                                return new QuestSelectionResult(state, QuestSelectionStatus.REROLLED, updatedUsed, limit);
                            });
                }));
    }

    public void clearOnlineDailyStates() {
        Bukkit.getOnlinePlayers().forEach(player -> stateCache.invalidate(player.getUniqueId()));
    }

    public void claimRetroactiveMilestonesForOnlinePlayers() {
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

        globalQuestService.progressMatching(player, type, amount, matcher, cause);

        PlayerQuestState state = cachedState(player.getUniqueId(), resetKey);
        if (state == null) {
            ensurePlayerStateAsync(player, resetKey);
            return List.of();
        }

        List<QuestProgressResult> results = new ArrayList<>();
        synchronized (state) {
            for (GeneratedQuest quest : state.quests()) {
                if (quest.completed() || !canAccessQuest(player, quest) || !quest.type().equals(type) || !matcher.test(quest)) {
                    continue;
                }

                int approvedAmount = approveProgress(player, quest, amount, cause);
                if (approvedAmount <= 0) {
                    continue;
                }

                int updatedProgress = Math.min(quest.goalAmount(), quest.progress() + approvedAmount);
                boolean completedNow = updatedProgress >= quest.goalAmount();
                GeneratedQuest updated = quest.withProgress(updatedProgress, completedNow);
                state.replaceQuest(updated);
                results.add(new QuestProgressResult(updated, completedNow));
                progressIndicatorService.showPersonal(player, updated, quest.progress());

                questRepository.updateProgress(updated)
                        .exceptionally(throwable -> {
                            log.error("Failed to persist QuestsPlus progress for quest {}.", updated.instanceId(), throwable);
                            return null;
                        });
                if (completedNow) {
                    completeQuest(player, state, updated);
                }
            }
        }
        return results;
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

        List<QuestProgressResult> results = new ArrayList<>();
        synchronized (state) {
            for (GeneratedQuest quest : state.quests()) {
                if (quest.completed() || !canAccessQuest(player, quest) || !quest.definitionId().equals(normalizedDefinitionId)) {
                    continue;
                }
                int approvedAmount = approveProgress(player, quest, amount, QuestProgressEvent.Cause.COMMAND);
                if (approvedAmount <= 0) {
                    continue;
                }

                int updatedProgress = Math.min(quest.goalAmount(), quest.progress() + approvedAmount);
                boolean completedNow = updatedProgress >= quest.goalAmount();
                GeneratedQuest updated = quest.withProgress(updatedProgress, completedNow);
                state.replaceQuest(updated);
                results.add(new QuestProgressResult(updated, completedNow));
                progressIndicatorService.showPersonal(player, updated, quest.progress());

                questRepository.updateProgress(updated)
                        .exceptionally(throwable -> {
                            log.error("Failed to persist QuestsPlus command progress for quest {}.", updated.instanceId(), throwable);
                            return null;
                        });
                if (completedNow) {
                    completeQuest(player, state, updated);
                }
            }
        }
        return results;
    }

    public List<QuestProgressResult> progressAdminGoal(Player player, String resetKey, int amount, QuestType type) {
        if (amount <= 0 || type == null) {
            return List.of();
        }

        PlayerQuestState state = cachedState(player.getUniqueId(), resetKey);
        if (state == null) {
            return List.of();
        }

        List<QuestProgressResult> results = new ArrayList<>();
        synchronized (state) {
            for (GeneratedQuest quest : state.quests()) {
                if (quest.completed() || !canAccessQuest(player, quest) || !quest.type().equals(type)) {
                    continue;
                }

                int approvedAmount = approveProgress(player, quest, amount, QuestProgressEvent.Cause.COMMAND);
                if (approvedAmount <= 0) {
                    continue;
                }

                int updatedProgress = Math.min(quest.goalAmount(), quest.progress() + approvedAmount);
                boolean completedNow = updatedProgress >= quest.goalAmount();
                GeneratedQuest updated = quest.withProgress(updatedProgress, completedNow);
                state.replaceQuest(updated);
                results.add(new QuestProgressResult(updated, completedNow));
                progressIndicatorService.showPersonal(player, updated, quest.progress());

                questRepository.updateProgress(updated)
                        .exceptionally(throwable -> {
                            log.error("Failed to persist QuestsPlus command progress for quest {}.", updated.instanceId(), throwable);
                            return null;
                        });
                if (completedNow) {
                    completeQuest(player, state, updated);
                }
            }
        }
        return results;
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
                    Placeholder.unparsed("quest_display_name", stripMiniMessageFallback(quest.displayName())),
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
        String output = template == null ? "" : template;
        Map<String, String> values = commonPlaceholders(quest, player);
        values.putAll(definitionService.variablePlaceholders(quest));
        putQuestNumbers(values, quest, true);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    String replaceRawVariableDisplayValues(String template, GeneratedQuest quest) {
        String output = template == null ? "" : template;
        Map<String, String> displayValues = definitionService.variablePlaceholders(quest);
        for (Map.Entry<String, String> entry : quest.variables().entrySet()) {
            String rawValue = entry.getValue();
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            String displayValue = displayValues.get(entry.getKey());
            if (displayValue == null) {
                displayValue = displayValues.get(QuestNames.placeholderKey(entry.getKey()));
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
        QuestDefinition definition = candidates.get(random.nextInt(candidates.size()));
        Map<String, String> variables = resolveVariables(definition);
        return definitionService.handler(definition.type())
                .createGeneratedQuest(definition, playerId, resetKey, variables)
                .withSlotIndex(slotIndex)
                .withPremium(isPremiumSlot(slotIndex));
    }

    private Map<String, String> resolveVariables(QuestDefinition definition) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : definition.selectorTypes().entrySet()) {
            String key = entry.getKey();
            QuestVariableSelector selector = definitionService.selector(entry.getValue());
            resolved.put(key, selector.select(definition.selectorValues().get(key), random));
        }
        return Map.copyOf(resolved);
    }

    private void handleCompletion(Player player, GeneratedQuest quest) {
        configProvider.get().getMessages().getQuestCompleted().send(player, Placeholder.unparsed("quest_display_name", stripMiniMessageFallback(quest.displayName())));

        List<String> commands = rewardCommands(player, quest);
        if (commands.isEmpty()) {
            return;
        }

        Scheduler.entity(player).run(task -> {
            for (String command : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceQuestRewardTokens(command, quest, player));
            }
        });
    }

    private void completeQuest(Player player, PlayerQuestState state, GeneratedQuest quest) {
        QuestDifficulty difficulty = definitionService.difficulty(quest.difficultyId());
        List<QuestMilestone> milestones = difficulty == null ? List.of() : difficulty.milestones();
        questRepository.incrementCompletionStats(player.getUniqueId(), quest.difficultyId(), milestones)
                .thenAccept(stats -> {
                    synchronized (state) {
                        state.setDifficultyCompletions(quest.difficultyId(), stats.difficultyCompleted());
                        for (QuestMilestone milestone : stats.newlyExecutedMilestones()) {
                            state.markMilestoneExecuted(milestone.difficultyId(), milestone.completed());
                        }
                    }
                    handleMilestoneRewards(player, quest, stats);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to persist QuestsPlus completion stats for {}.", player.getUniqueId(), throwable);
                    return null;
                });
        handleCompletion(player, quest);
        streakService.evaluateQuestCompletion(player, state, accessibleQuests(player, state))
                .exceptionally(throwable -> {
                    log.error("Failed to evaluate QuestsPlus streak for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    private CompletableFuture<Void> claimRetroactiveMilestones(Player player, PlayerQuestState state) {
        return questRepository.claimEligibleMilestones(player.getUniqueId(), state.difficultyCompletions(), definitionService.difficulties())
                .thenAccept(claims -> {
                    if (claims.isEmpty()) {
                        return;
                    }
                    synchronized (state) {
                        for (QuestMilestoneClaim claim : claims) {
                            state.markMilestoneExecuted(claim.difficultyId(), claim.milestone().completed());
                        }
                    }
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
                Placeholder.unparsed("quest_difficulty", stripMiniMessageFallback(difficultyDisplayName)),
                Placeholder.unparsed("difficulty_id", difficultyId),
                Placeholder.unparsed("quests_completed", QuestNumberFormatter.format(questsCompleted))
        );
    }

    private void handleMilestoneRewards(Player player, GeneratedQuest quest, QuestCompletionStats stats) {
        if (stats.newlyExecutedMilestones().isEmpty()) {
            return;
        }

        Scheduler.entity(player).run(task -> {
            for (QuestMilestone milestone : stats.newlyExecutedMilestones()) {
                if (player.isOnline()) {
                    sendMilestoneCompletedMessage(player, quest, stats, milestone);
                }
                for (String command : milestone.commands()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceMilestoneTokens(command, player, quest, stats, milestone));
                }
            }
        });
    }

    private void handleMilestoneClaimRewards(Player player, List<QuestMilestoneClaim> claims) {
        if (claims.isEmpty()) {
            return;
        }
        Scheduler.entity(player).run(task -> {
            if (!player.isOnline()) {
                return;
            }
            for (QuestMilestoneClaim claim : claims) {
                sendMilestoneClaimedMessage(player, claim);
                for (String command : claim.milestone().commands()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceMilestoneClaimTokens(command, player, claim));
                }
            }
        });
    }

    private void sendMilestoneCompletedMessage(Player player, GeneratedQuest quest, QuestCompletionStats stats, QuestMilestone milestone) {
        configProvider.get().getMessages().getMilestoneCompleted().send(
                player,
                Placeholder.unparsed("quest_display_name", stripMiniMessageFallback(quest.displayName())),
                Placeholder.unparsed("quest_difficulty", stripMiniMessageFallback(quest.difficultyDisplayName())),
                Placeholder.unparsed("difficulty", stripMiniMessageFallback(quest.difficultyDisplayName())),
                Placeholder.unparsed("difficulty_id", quest.difficultyId()),
                Placeholder.unparsed("completed", QuestNumberFormatter.format(stats.difficultyCompleted())),
                Placeholder.unparsed("milestone_completed", QuestNumberFormatter.format(milestone.completed())),
                Placeholder.unparsed("milestone_display_name", stripMiniMessageFallback(milestone.displayName()))
        );
    }

    private void sendMilestoneClaimedMessage(Player player, QuestMilestoneClaim claim) {
        configProvider.get().getMessages().getMilestoneClaimed().send(
                player,
                Placeholder.unparsed("quest_difficulty", stripMiniMessageFallback(claim.difficultyDisplayName())),
                Placeholder.unparsed("difficulty", stripMiniMessageFallback(claim.difficultyDisplayName())),
                Placeholder.unparsed("difficulty_id", claim.difficultyId()),
                Placeholder.unparsed("completed", QuestNumberFormatter.format(claim.difficultyCompleted())),
                Placeholder.unparsed("milestone_completed", QuestNumberFormatter.format(claim.milestone().completed())),
                Placeholder.unparsed("milestone_display_name", stripMiniMessageFallback(claim.milestone().displayName()))
        );
    }

    private String replaceMilestoneTokens(String template, Player player, GeneratedQuest quest, QuestCompletionStats stats, QuestMilestone milestone) {
        String output = template == null ? "" : template;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("difficulty", quest.difficultyDisplayName());
        values.put("difficulty_id", quest.difficultyId());
        values.put("completed", Integer.toString(stats.difficultyCompleted()));
        values.put("milestone_completed", Integer.toString(milestone.completed()));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    private String replaceMilestoneClaimTokens(String template, Player player, QuestMilestoneClaim claim) {
        String output = template == null ? "" : template;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("difficulty", claim.difficultyDisplayName());
        values.put("difficulty_id", claim.difficultyId());
        values.put("completed", Integer.toString(claim.difficultyCompleted()));
        values.put("milestone_completed", Integer.toString(claim.milestone().completed()));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    private List<String> rewardCommands(Player player, GeneratedQuest quest) {
        List<String> commands = new ArrayList<>();
        QuestDifficulty difficulty = definitionService.difficulty(quest.difficultyId());
        if (difficulty != null) {
            randomDifficultyRewardCommand(difficulty.rewardCommands()).ifPresent(commands::add);
        }
        if (quest.premium() && canAccessQuest(player, quest)) {
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
        definitionService.definitions().stream()
                .filter(definition -> definition.id().equals(quest.definitionId()))
                .findFirst()
                .map(QuestDefinition::rewardCommands)
                .ifPresent(commands::addAll);
        return commands;
    }

    private java.util.Optional<String> randomDifficultyRewardCommand(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(commands.get(random.nextInt(commands.size())));
    }

    private Map<String, String> commonPlaceholders(GeneratedQuest quest, Player player) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("quest_id", quest.definitionId());
        values.put("quest_display_name", stripMiniMessageFallback(quest.displayName()));
        values.put("quest_difficulty", quest.difficultyDisplayName());
        values.put("quest_difficulty_id", quest.difficultyId());
        putQuestNumbers(values, quest, true);
        return values;
    }

    private String replaceQuestRewardTokens(String template, GeneratedQuest quest, Player player) {
        String output = template == null ? "" : template;
        Map<String, String> values = commonPlaceholders(quest, player);
        values.putAll(definitionService.variablePlaceholders(quest));
        putQuestNumbers(values, quest, false);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    private void putQuestNumbers(Map<String, String> values, GeneratedQuest quest, boolean formatted) {
        values.put("goal_amount", number(quest.goalAmount(), formatted));
        values.put("goal-amount", number(quest.goalAmount(), formatted));
        values.put("progress", number(quest.progress(), formatted));
    }

    private String number(int value, boolean formatted) {
        return formatted ? QuestNumberFormatter.format(value) : Integer.toString(value);
    }

    private String stripMiniMessageFallback(String input) {
        return input == null ? "" : input.replaceAll("<[^>]+>", "").trim();
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

    private record RerollUsageKey(UUID playerId, String resetKey) {
    }

    private record ResetPurchaseUsageKey(UUID playerId, String resetKey) {
    }
}
