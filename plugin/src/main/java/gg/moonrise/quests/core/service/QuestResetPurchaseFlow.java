package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import gg.moonrise.quests.util.QuestNumberFormatter;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runs a quest reset purchase: checks eligibility and the daily limit, charges the chosen currency, resets the
 * player's quests and refunds the charge whenever the reset does not apply.
 */
@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestResetPurchaseFlow {

    private final ConfigProvider configProvider;
    private final QuestService questService;
    private final QuestResetService resetService;
    private final QuestResetPurchaseService resetPurchaseService;
    private final QuestResetCurrencies currencies;

    /**
     * Buys a quest reset for {@code player} with the currency registered under {@code currencyKey}. {@code menus} is
     * used to reopen the daily quest or reset purchase menu once the purchase settles.
     */
    public void purchase(Player player, QuestCurrencyKey currencyKey, QuestMenuService menus) {
        if (!resetPurchaseService.begin(player)) {
            config().getMessages().getQuestResetProcessing().send(player);
            return;
        }

        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> questService.refreshQuestResetPurchaseUsageAsync(player.getUniqueId(), resetKey).thenApply(unused -> state))
                .whenComplete((state, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to load QuestsPlus state before reset purchase choice {} for {}.", currencyKey, player.getUniqueId(), throwable);
                        settleResetPurchase(player, null, online -> config().getMessages().getQuestResetFailed().send(online));
                        return;
                    }
                    runOnPlayer(
                            player,
                            task -> processResetPurchase(player, currencyKey, resetKey, state, menus),
                            () -> resetPurchaseService.finish(player)
                    );
                });
    }

    public void sendPurchaseUnavailable(Player player, @Nullable QuestCurrency currency, QuestResetEligibility eligibility) {
        config().getMessages().getQuestResetPurchaseUnavailable().send(
                player,
                Placeholder.unparsed("payment", currencies.displayName(currency, "Quest Reset currency")),
                Placeholder.unparsed("reward", currencies.displayName(currency, "Quest Reset currency")),
                Placeholder.unparsed("amount", currencies.displayAmount(currency, player)),
                Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required())),
                Placeholder.unparsed("resets_used", QuestNumberFormatter.format(questService.cachedQuestResetPurchasesUsed(player.getUniqueId(), resetService.currentResetKey()))),
                Placeholder.unparsed("resets_limit", QuestNumberFormatter.format(questService.questResetDailyLimit())),
                Placeholder.unparsed("resets_remaining", QuestNumberFormatter.format(questService.questResetPurchasesRemaining(player.getUniqueId(), resetService.currentResetKey())))
        );
    }

    public void sendResetLimitReached(Player player, QuestResetEligibility eligibility) {
        String resetKey = resetService.currentResetKey();
        config().getMessages().getQuestResetLimitReached().send(
                player,
                Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required())),
                Placeholder.unparsed("resets_used", QuestNumberFormatter.format(questService.cachedQuestResetPurchasesUsed(player.getUniqueId(), resetKey))),
                Placeholder.unparsed("resets_limit", QuestNumberFormatter.format(questService.questResetDailyLimit())),
                Placeholder.unparsed("resets_remaining", QuestNumberFormatter.format(questService.questResetPurchasesRemaining(player.getUniqueId(), resetKey)))
        );
    }

    private void processResetPurchase(Player player, QuestCurrencyKey currencyKey, String resetKey, PlayerQuestState state, QuestMenuService menus) {
        try {
            if (!player.isOnline()) {
                resetPurchaseService.finish(player);
                return;
            }
            QuestResetEligibility eligibility = questService.resetEligibility(player, state);
            if (!eligibility.eligible()) {
                try {
                    config().getMessages().getQuestResetNotReady().send(
                            player,
                            Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                            Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required()))
                    );
                    menus.openDailyQuests(player);
                } finally {
                    resetPurchaseService.finish(player);
                }
                return;
            }
            if (questService.questResetPurchasesRemaining(player.getUniqueId(), resetKey) <= 0) {
                try {
                    sendResetLimitReached(player, eligibility);
                    menus.openDailyQuests(player);
                } finally {
                    resetPurchaseService.finish(player);
                }
                return;
            }
            // The purchase helpers below treat a missing currency as unavailable.
            QuestCurrency currency = currencies.registeredCurrency(currencyKey).orElse(null);
            if (!currencies.canShowQuestResetPayment(currency) || !currencies.charge(player, currency)) {
                try {
                    sendPurchaseUnavailable(player, currency, eligibility);
                    menus.openQuestResetPurchase(player);
                } finally {
                    resetPurchaseService.finish(player);
                }
                return;
            }
            resetAfterCharge(player, currencyKey, resetKey, currency, eligibility, menus);
        } catch (RuntimeException exception) {
            try {
                log.error("Failed to process QuestsPlus reset purchase choice {} for {}.", currencyKey, player.getUniqueId(), exception);
                if (player.isOnline()) {
                    config().getMessages().getQuestResetFailed().send(player);
                }
            } finally {
                resetPurchaseService.finish(player);
            }
            throw exception;
        }
    }

    /**
     * Applies the reset after the currency was charged. Every outcome other than an applied reset
     * refunds the charge, including when the player logged out before the database answered.
     */
    private void resetAfterCharge(
            Player player,
            QuestCurrencyKey currencyKey,
            String resetKey,
            QuestCurrency currency,
            QuestResetEligibility eligibility,
            QuestMenuService menus
    ) {
        CompletableFuture<QuestRepository.ResetPurchaseOutcome> reset;
        try {
            reset = questService.recordQuestResetPurchaseAndReset(player.getUniqueId(), resetKey);
        } catch (RuntimeException exception) {
            reset = CompletableFuture.failedFuture(exception);
        }
        reset.whenComplete((outcome, throwable) -> {
            if (throwable != null || outcome == null) {
                log.error("Failed to reset QuestsPlus after purchase choice {} for {}.", currencyKey, player.getUniqueId(), throwable);
                settleResetPurchase(player, currency, online -> config().getMessages().getQuestResetFailed().send(online));
                return;
            }
            switch (outcome) {
                case QuestRepository.ResetPurchaseOutcome.Applied applied -> settleResetPurchase(player, null, online -> {
                    sendResetSuccess(online, currency, eligibility, resetKey);
                    menus.openDailyQuests(online);
                });
                case QuestRepository.ResetPurchaseOutcome.LimitReached limitReached -> settleResetPurchase(player, currency, online -> {
                    sendResetLimitReached(online, eligibility);
                    menus.openDailyQuests(online);
                });
            }
        });
    }

    /**
     * Ends a reset purchase exactly once: refunds {@code refund} when it is not null, releases the
     * purchase lock and, only if the player is still online, notifies them. The refund and lock
     * release also run when the player's entity scheduler has retired because they logged out.
     */
    private void settleResetPurchase(Player player, @Nullable QuestCurrency refund, Consumer<Player> notifyOnline) {
        AtomicBoolean settled = new AtomicBoolean();
        Runnable settle = () -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            try {
                if (refund != null) {
                    currencies.refund(player, refund);
                }
            } finally {
                resetPurchaseService.finish(player);
            }
        };
        runOnPlayer(player, task -> {
            settle.run();
            if (player.isOnline()) {
                notifyOnline.accept(player);
            }
        }, settle);
    }

    /**
     * Runs {@code action} on the player's entity scheduler. When the player is already gone (the
     * scheduler is retired or rejects the task), {@code retired} runs instead: directly from the
     * retired callback, or on the global scheduler when the task could not be scheduled at all.
     */
    private void runOnPlayer(Player player, Consumer<ScheduledTask> action, Runnable retired) {
        try {
            if (Scheduler.entity(player).run(action, retired) != null) {
                return;
            }
        } catch (RuntimeException exception) {
            log.error("Failed to schedule QuestsPlus task for {}; running its fallback instead.", player.getUniqueId(), exception);
        }
        runGlobal(retired);
    }

    private void runGlobal(Runnable action) {
        try {
            Scheduler.sync().run(task -> action.run());
        } catch (RuntimeException exception) {
            log.error("Failed to schedule QuestsPlus global task; running it inline.", exception);
            action.run();
        }
    }

    private void sendResetSuccess(Player player, QuestCurrency currency, QuestResetEligibility eligibility, String resetKey) {
        config().getMessages().getQuestResetSuccess().send(
                player,
                Placeholder.unparsed("payment", currencies.displayName(currency, "")),
                Placeholder.unparsed("reward", currencies.displayName(currency, "")),
                Placeholder.unparsed("amount", currencies.displayAmount(currency, player)),
                Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required())),
                Placeholder.unparsed("resets_used", QuestNumberFormatter.format(questService.cachedQuestResetPurchasesUsed(player.getUniqueId(), resetKey))),
                Placeholder.unparsed("resets_limit", QuestNumberFormatter.format(questService.questResetDailyLimit())),
                Placeholder.unparsed("resets_remaining", QuestNumberFormatter.format(questService.questResetPurchasesRemaining(player.getUniqueId(), resetKey)))
        );
    }

    private Config config() {
        return configProvider.get();
    }
}
