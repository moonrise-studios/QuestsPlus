package gg.moonrise.quests.infrastructure.currency;

import gg.moonrise.moss.spring.Enableable;
import gg.moonrise.quests.config.currency.Currencies;
import gg.moonrise.quests.config.menu.MenuButton;
import gg.moonrise.quests.config.menu.MenuItem;
import gg.moonrise.quests.config.currency.VaultCurrencyConfig;
import gg.moonrise.quests.config.ConfigProvider;
import gg.moonrise.quests.sdk.currency.QuestCurrencies;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

@Slf4j(topic = "QuestsPlus")
@RequiredArgsConstructor
public class VaultCurrency implements QuestCurrency, Enableable {

    private final ConfigProvider configProvider;
    private Economy economy;

    @Override
    public QuestCurrencyKey key() {
        return QuestCurrencies.VAULT;
    }

    @Override
    public String displayName() {
        return config().getDisplayName();
    }

    @Override
    public String displayAmount(Player player) {
        double amount = questResetCost();
        return economy == null ? Double.toString(amount) : economy.format(amount);
    }

    @Override
    public double questResetCost() {
        return Math.max(0.0D, config().getQuestResetCost());
    }

    @Override
    public QuestCurrencyButton button() {
        MenuButton button = config().getButton();
        MenuButton fallback = new VaultCurrencyConfig().getButton();
        MenuButton resolvedButton = button == null ? fallback : button;
        MenuItem item = resolvedButton.getItem() == null ? fallback.getItem() : resolvedButton.getItem();
        return QuestCurrencyButton.of(resolvedButton.getSlot(), item.getMaterial(), item.getName(), item.getLore());
    }

    @Override
    public boolean charge(Player player, double amount) {
        if (economy == null) {
            log.warn("Vault currency charge rejected for {} because Vault economy is unavailable.", player.getUniqueId());
            return false;
        }
        double chargeAmount = Math.max(0.0D, amount);
        if (chargeAmount <= 0.0D) {
            return true;
        }
        EconomyResponse response = economy.withdrawPlayer(player, chargeAmount);
        return response.transactionSuccess();
    }

    @Override
    public boolean refund(Player player, double amount) {
        if (economy == null) {
            log.warn("Vault currency refund rejected for {} because Vault economy is unavailable.", player.getUniqueId());
            return false;
        }
        double refundAmount = Math.max(0.0D, amount);
        if (refundAmount <= 0.0D) {
            return true;
        }
        EconomyResponse response = economy.depositPlayer(player, refundAmount);
        return response.transactionSuccess();
    }

    @Override
    public boolean isAvailable() {
        return economy != null || resolveEconomy().isPresent();
    }

    @Override
    public void onEnable() {
        resolveEconomy();
    }

    private Optional<Economy> resolveEconomy() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return Optional.empty();
        }

        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return Optional.empty();
        }

        economy = provider.getProvider();
        return Optional.ofNullable(economy);
    }

    private VaultCurrencyConfig config() {
        Currencies currencies = configProvider.get().getCurrencies();
        return currencies == null || currencies.getVault() == null ? new VaultCurrencyConfig() : currencies.getVault();
    }
}
