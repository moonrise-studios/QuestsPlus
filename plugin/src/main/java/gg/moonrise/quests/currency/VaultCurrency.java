package gg.moonrise.quests.currency;

import gg.moonrise.moss.spring.Enableable;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.ConfigProvider;
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
        Config.MenuButton button = config().getButton();
        Config.MenuButton fallback = new Config.VaultCurrency().getButton();
        Config.MenuButton resolvedButton = button == null ? fallback : button;
        Config.MenuItem item = resolvedButton.getItem() == null ? fallback.getItem() : resolvedButton.getItem();
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
    public boolean isAvailable() {
        return economy != null || resolveEconomy() != null;
    }

    @Override
    public void onEnable() {
        resolveEconomy();
    }

    private Economy resolveEconomy() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return null;
        }

        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return null;
        }

        economy = provider.getProvider();
        return economy;
    }

    private Config.VaultCurrency config() {
        Config.Currencies currencies = configProvider.get().getCurrencies();
        return currencies == null || currencies.getVault() == null ? new Config.VaultCurrency() : currencies.getVault();
    }
}
