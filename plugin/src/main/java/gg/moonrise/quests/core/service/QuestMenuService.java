package gg.moonrise.quests.core.service;

import gg.moonrise.engine.message.Message;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.model.QuestSelectionResult;
import gg.moonrise.quests.model.QuestSelectionStatus;
import gg.moonrise.quests.model.QuestStreakState;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.ui.GlobalRewardPreviewUI;
import gg.moonrise.quests.ui.QuestDifficultyPickerUI;
import gg.moonrise.quests.ui.QuestMenuUI;
import gg.moonrise.quests.ui.QuestMilestoneMenuUI;
import gg.moonrise.quests.ui.QuestMilestoneSelectorUI;
import gg.moonrise.quests.ui.QuestResetPurchaseUI;
import gg.moonrise.quests.ui.QuestStreakConfirmUI;
import gg.moonrise.quests.ui.QuestStreakMenuUI;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Opens the QuestsPlus menus and handles their click actions. Rendering lives in {@link QuestMenuItems}, reset
 * currency checks in {@link QuestResetCurrencies} and the reset purchase itself in {@link QuestResetPurchaseFlow};
 * the menus reach those through {@link #items()} and {@link #currencies()}.
 */
@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestMenuService {

    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final QuestService questService;
    private final QuestSlotService slotService;
    private final QuestUsageService usageService;
    private final QuestSelectionService selectionService;
    private final QuestResetService resetService;
    private final QuestStreakService streakService;
    private final GlobalQuestService globalQuestService;
    private final QuestMenuItems items;
    private final QuestResetCurrencies currencies;
    private final QuestResetPurchaseFlow resetPurchaseFlow;

    public Config config() {
        return configProvider.get();
    }

    public QuestMenuItems items() {
        return items;
    }

    public QuestResetCurrencies currencies() {
        return currencies;
    }

    public void openDailyQuests(Player player) {
        String resetKey = resetService.currentResetKey();
        CompletableFuture<DailyMenuState> load = questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> usageService.ensureRerollUsageAsync(player.getUniqueId(), resetKey).thenApply(unused -> state))
                .thenCompose(state -> usageService.ensureQuestResetPurchaseUsageAsync(player.getUniqueId(), resetKey).thenApply(unused -> state))
                .thenCompose(state -> globalQuestService.activeStateAsync().thenApply(globalState -> new DailyMenuState(state, globalState.orElse(null))));
        openWhenLoaded(player, load, "open QuestsPlus menu", Config.Messages::getMenuOpenFailed,
                menuState -> new QuestMenuUI(player, this, menuState.questState(), menuState.globalState()).open());
    }

    public void openMilestoneSelector(Player player) {
        if (!config().isQuestMilestonesEnabled()) {
            config().getMessages().getQuestMilestonesDisabled().send(player);
            return;
        }
        String resetKey = resetService.currentResetKey();
        openWhenLoaded(player, questService.ensurePlayerStateAsync(player, resetKey), "open QuestsPlus milestone selector", Config.Messages::getMilestonesOpenFailed,
                state -> new QuestMilestoneSelectorUI(player, this, state).open());
    }

    public void openMilestones(Player player, String difficultyId) {
        if (!config().isQuestMilestonesEnabled()) {
            config().getMessages().getQuestMilestonesDisabled().send(player);
            return;
        }
        String normalizedDifficulty = QuestNames.normalize(difficultyId);
        // difficulty() falls back to the default difficulty, so only an exact id match counts here.
        Optional<QuestDifficulty> match = definitionService.difficulty(normalizedDifficulty)
                .filter(candidate -> candidate.id().equals(normalizedDifficulty));
        if (match.isEmpty()) {
            config().getMessages().getUnknownQuestDifficulty().send(player);
            return;
        }
        QuestDifficulty difficulty = match.get();

        String resetKey = resetService.currentResetKey();
        openWhenLoaded(player, questService.ensurePlayerStateAsync(player, resetKey), "open QuestsPlus milestones", Config.Messages::getMilestonesOpenFailed,
                state -> new QuestMilestoneMenuUI(player, this, state, difficulty).open());
    }

    public void openStreaks(Player player) {
        if (!streakService.isEnabled()) {
            config().getMessages().getQuestStreaksDisabled().send(player);
            return;
        }
        openWhenLoaded(player, streakMenuState(player), "open QuestsPlus streak menu", Config.Messages::getStreaksOpenFailed,
                menuState -> new QuestStreakMenuUI(player, this, menuState.questState(), menuState.streakState()).open());
    }

    public void openStreakConfirmation(Player player) {
        if (!streakService.isEnabled()) {
            config().getMessages().getQuestStreaksDisabled().send(player);
            return;
        }
        openWhenLoaded(player, streakMenuState(player), "open QuestsPlus streak confirmation", Config.Messages::getStreakConfirmationOpenFailed,
                menuState -> new QuestStreakConfirmUI(player, this, menuState.questState(), menuState.streakState()).open());
    }

    public void openQuestResetPurchase(Player player) {
        String resetKey = resetService.currentResetKey();
        CompletableFuture<PlayerQuestState> load = questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> usageService.refreshQuestResetPurchaseUsageAsync(player.getUniqueId(), resetKey).thenApply(unused -> state));
        openWhenLoaded(player, load, "open QuestsPlus reset purchase menu", Config.Messages::getQuestResetPurchaseOpenFailed, state -> {
            QuestResetEligibility eligibility = slotService.resetEligibility(player, state);
            if (!eligibility.eligible()) {
                config().getMessages().getQuestResetNotReady().send(
                        player,
                        Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                        Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required()))
                );
                new QuestMenuUI(player, this, state, globalQuestService.cachedActiveState().orElse(null)).open();
                return;
            }
            if (usageService.questResetPurchasesRemaining(player.getUniqueId(), resetKey) <= 0) {
                resetPurchaseFlow.sendResetLimitReached(player, eligibility);
                new QuestMenuUI(player, this, state, globalQuestService.cachedActiveState().orElse(null)).open();
                return;
            }
            if (!currencies.hasAvailableQuestResetCurrency()) {
                resetPurchaseFlow.sendPurchaseUnavailable(player, null, eligibility);
                new QuestMenuUI(player, this, state, globalQuestService.cachedActiveState().orElse(null)).open();
                return;
            }
            new QuestResetPurchaseUI(player, this, eligibility).open();
        });
    }

    public void openDifficultyPicker(Player player, int slotIndex) {
        String resetKey = resetService.currentResetKey();
        openWhenLoaded(player, questService.ensurePlayerStateAsync(player, resetKey), "open QuestsPlus difficulty picker", Config.Messages::getDifficultyPickerOpenFailed,
                state -> new QuestDifficultyPickerUI(player, this, slotIndex, false).open());
    }

    public void openRerollDifficultyPicker(Player player, int slotIndex) {
        String resetKey = resetService.currentResetKey();
        CompletableFuture<PlayerQuestState> load = questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> usageService.ensureRerollUsageAsync(player.getUniqueId(), resetKey).thenApply(used -> state));
        openWhenLoaded(player, load, "open QuestsPlus reroll difficulty picker", Config.Messages::getRerollPickerOpenFailed, state -> {
            Optional<GeneratedQuest> slotQuest = state.questAtSlot(slotIndex);
            if (slotQuest.isEmpty()) {
                openDifficultyPicker(player, slotIndex);
                return;
            }
            GeneratedQuest quest = slotQuest.get();
            if (!slotService.canAccessQuest(player, quest)) {
                openDailyQuests(player);
                return;
            }
            if (quest.completed()) {
                config().getMessages().getCompletedQuestCannotReroll().send(player);
                return;
            }
            if (usageService.rerollsRemaining(player, resetKey) <= 0) {
                config().getMessages().getNoRerollsRemaining().send(player);
                return;
            }
            new QuestDifficultyPickerUI(player, this, slotIndex, true).open();
        });
    }

    public void selectDifficultyForSlot(Player player, int slotIndex, String difficultyId, boolean reroll) {
        String resetKey = resetService.currentResetKey();
        CompletableFuture<QuestSelectionResult> selection = reroll
                ? selectionService.rerollQuestDifficulty(player, resetKey, slotIndex, difficultyId)
                : selectionService.selectQuestDifficulty(player, resetKey, slotIndex, difficultyId);
        openWhenLoaded(player, selection, "select QuestsPlus difficulty " + difficultyId, Config.Messages::getDifficultySelectFailed, result -> {
            if (result.status() == QuestSelectionStatus.NO_AVAILABLE_QUEST) {
                String normalizedDifficulty = QuestNames.normalize(difficultyId);
                Component difficultyName = items.difficultyName(normalizedDifficulty);
                config().getMessages().getNoAvailableQuestForDifficulty().send(
                        player,
                        Placeholder.unparsed("difficulty_id", normalizedDifficulty),
                        Placeholder.component("difficulty", difficultyName),
                        Placeholder.component("quest_difficulty", difficultyName)
                );
            } else if (result.status() == QuestSelectionStatus.REQUIREMENTS_NOT_MET) {
                String normalizedDifficulty = QuestNames.normalize(difficultyId);
                String requirements = definitionService.difficulty(normalizedDifficulty)
                        .map(difficulty -> difficultyRequirementsText(difficulty, result.state()))
                        .orElse("");
                Component difficultyName = items.difficultyName(normalizedDifficulty);
                config().getMessages().getDifficultyRequirementsNotMet().send(
                        player,
                        Placeholder.unparsed("difficulty_id", normalizedDifficulty),
                        Placeholder.unparsed("requirements", requirements),
                        Placeholder.component("difficulty", difficultyName),
                        Placeholder.component("quest_difficulty", difficultyName)
                );
            } else if (result.status() == QuestSelectionStatus.NO_REROLLS) {
                config().getMessages().getNoRerollsRemaining().send(player);
            } else if (result.status() == QuestSelectionStatus.COMPLETED) {
                config().getMessages().getCompletedQuestCannotReroll().send(player);
            } else if (result.status() == QuestSelectionStatus.REROLLED) {
                config().getMessages().getQuestRerolled().send(
                        player,
                        Placeholder.unparsed("slot", QuestNumberFormatter.format(slotIndex + 1)),
                        Placeholder.unparsed("rerolls_remaining", QuestNumberFormatter.format(Math.max(0, result.rerollsLimit() - result.rerollsUsed())))
                );
            }
            new QuestMenuUI(player, this, result.state(), globalQuestService.cachedActiveState().orElse(null)).open();
        });
    }

    public void openGlobalRewardPreview(Player player) {
        Scheduler.entity(player).run(task -> {
            if (player.isOnline()) {
                new GlobalRewardPreviewUI(player, this).open();
            }
        });
    }

    public void applyStreakRecovery(Player player) {
        if (!streakService.isEnabled()) {
            config().getMessages().getQuestStreaksDisabled().send(player);
            return;
        }
        streakService.recoverStreak(player, resetService.currentResetKey())
                .thenAccept(result -> Scheduler.entity(player).run(task -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (result.applied()) {
                        config().getMessages().getStreakRecoveryApplied().send(player, Placeholder.unparsed("streak", QuestNumberFormatter.format(result.evaluation().state().currentStreak())));
                    } else {
                        config().getMessages().getStreakRecoveryUnavailable().send(player);
                    }
                    openStreaks(player);
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to recover QuestsPlus streak for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    public void applyQuestResetPurchase(Player player, QuestCurrencyKey currencyKey) {
        resetPurchaseFlow.purchase(player, currencyKey, this);
    }

    private CompletableFuture<StreakMenuState> streakMenuState(Player player) {
        String resetKey = resetService.currentResetKey();
        return questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(questState -> streakService.ensureState(player, resetKey).thenApply(streakState -> new StreakMenuState(questState, streakState)));
    }

    /**
     * Waits for {@code load}, then runs {@code open} on the player's entity thread if they are
     * still online. If loading fails, logs the failure and sends the configured failure message.
     */
    private <T> void openWhenLoaded(
            Player player,
            CompletableFuture<T> load,
            String action,
            Function<Config.Messages, Message> failureMessage,
            Consumer<T> open
    ) {
        load.thenAccept(value -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        open.accept(value);
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to {} for {}.", action, player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            failureMessage.apply(config().getMessages()).send(player);
                        }
                    });
                    return null;
                });
    }

    private String difficultyRequirementsText(QuestDifficulty difficulty, PlayerQuestState state) {
        Map<String, Integer> unmet = difficulty.unmetRequirements(state);
        if (unmet.isEmpty()) {
            return "";
        }

        List<String> requirements = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : unmet.entrySet()) {
            String count = QuestNumberFormatter.format(entry.getValue());
            String noun = entry.getValue() == 1 ? "quest" : "quests";
            requirements.add(count + " " + entry.getKey() + " " + noun);
        }
        return String.join(", ", requirements);
    }

    private record StreakMenuState(PlayerQuestState questState, QuestStreakState streakState) {
    }

    private record DailyMenuState(PlayerQuestState questState, GlobalQuestState globalState) {
    }
}
