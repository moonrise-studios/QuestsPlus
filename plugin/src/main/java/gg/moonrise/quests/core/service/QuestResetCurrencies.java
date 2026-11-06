package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.Currencies;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Guards every call into a {@link QuestCurrency} used to pay for quest resets: a currency that throws is treated as
 * unavailable (and logged) instead of breaking the menu or the purchase.
 */
@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestResetCurrencies {

    private final ConfigProvider configProvider;
    private final QuestResetPurchaseService resetPurchaseService;

    public List<QuestCurrency> questResetCurrencies() {
        return resetPurchaseService.registeredCurrencies().stream()
                .filter(this::canShowQuestResetPayment)
                .toList();
    }

    public boolean canShowQuestResetPayment(@Nullable QuestCurrency currency) {
        return currency != null && currencyEnabled(currency) && currencyAvailable(currency) && questResetPaymentButton(currency).isPresent();
    }

    /**
     * The currency's purchase button, or empty when there is no currency, it has no button, or rendering the button
     * failed (the failure is logged).
     */
    public Optional<QuestCurrencyButton> questResetPaymentButton(@Nullable QuestCurrency currency) {
        if (currency == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(currency.button());
        } catch (RuntimeException exception) {
            log.warn("QuestsPlus currency {} failed to render its purchase button.", currencyKey(currency), exception);
            return Optional.empty();
        }
    }

    public boolean hasAvailableQuestResetCurrency() {
        for (QuestCurrency currency : resetPurchaseService.registeredCurrencies()) {
            if (canShowQuestResetPayment(currency)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The currency registered under {@code key}, or empty when none is registered or its owning plugin is disabled.
     */
    public Optional<QuestCurrency> registeredCurrency(QuestCurrencyKey key) {
        return resetPurchaseService.registeredCurrency(key);
    }

    public boolean charge(Player player, @Nullable QuestCurrency currency) {
        try {
            return currency != null && currency.charge(player, Math.max(0.0D, currency.questResetCost()));
        } catch (RuntimeException exception) {
            log.warn("QuestsPlus currency {} failed to charge {}.", currencyKey(currency), player.getUniqueId(), exception);
            return false;
        }
    }

    public void refund(Player player, @Nullable QuestCurrency currency) {
        if (currency == null) {
            return;
        }
        double amount = Math.max(0.0D, currency.questResetCost());
        try {
            if (!currency.refund(player, amount)) {
                log.error("QuestsPlus currency {} did not refund {} after a failed reset purchase.", currencyKey(currency), player.getUniqueId());
            }
        } catch (RuntimeException exception) {
            log.error("QuestsPlus currency {} failed to refund {} after a failed reset purchase.", currencyKey(currency), player.getUniqueId(), exception);
        }
    }

    public String displayName(@Nullable QuestCurrency currency, String fallback) {
        if (currency == null) {
            return fallback;
        }
        try {
            return Objects.requireNonNullElse(currency.displayName(), fallback);
        } catch (RuntimeException exception) {
            log.warn("QuestsPlus currency {} failed to render its display name.", currencyKey(currency), exception);
            return fallback;
        }
    }

    public String displayAmount(@Nullable QuestCurrency currency, @Nullable Player player) {
        if (currency == null) {
            return "";
        }
        try {
            return Objects.requireNonNullElse(currency.displayAmount(player), "");
        } catch (RuntimeException exception) {
            log.warn("QuestsPlus currency {} failed to render its display amount.", currencyKey(currency), exception);
            return "";
        }
    }

    private boolean currencyAvailable(QuestCurrency currency) {
        try {
            return currency != null && currency.isAvailable();
        } catch (RuntimeException exception) {
            log.warn("QuestsPlus currency {} failed its availability check.", currencyKey(currency), exception);
            return false;
        }
    }

    private boolean currencyEnabled(QuestCurrency currency) {
        if (currency == null || currency.key() == null) {
            return false;
        }
        Currencies currencies = configProvider.get().getCurrencies();
        List<String> enabledCurrencies = currencies == null || currencies.getEnabledCurrencies() == null
                ? List.of()
                : currencies.getEnabledCurrencies();
        for (String enabledCurrency : enabledCurrencies) {
            try {
                if (currency.key().equals(QuestCurrencyKey.of(enabledCurrency))) {
                    return true;
                }
            } catch (IllegalArgumentException exception) {
                log.warn("Ignoring invalid currency key '{}' in currencies.yml enabled-currencies.", enabledCurrency);
            }
        }
        return false;
    }

    private String currencyKey(@Nullable QuestCurrency currency) {
        if (currency == null || currency.key() == null) {
            return "unknown";
        }
        return currency.key().key();
    }
}
