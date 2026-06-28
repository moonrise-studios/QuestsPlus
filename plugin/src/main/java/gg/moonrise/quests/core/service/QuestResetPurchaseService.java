package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.QuestResetPaymentType;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestResetPurchaseService {

    private static final ThreadLocal<DecimalFormat> MONEY_FORMAT = ThreadLocal.withInitial(() ->
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US))
    );

    private final ConfigProvider configProvider;
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();

    public boolean begin(Player player) {
        return processingPlayers.add(player.getUniqueId());
    }

    public void finish(Player player) {
        processingPlayers.remove(player.getUniqueId());
    }

    public boolean isProcessing(Player player) {
        return processingPlayers.contains(player.getUniqueId());
    }

    public boolean hasAvailablePaymentMethods() {
        for (QuestResetPaymentType type : QuestResetPaymentType.values()) {
            if (isAvailable(type)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEnabled(QuestResetPaymentType type) {
        return switch (type) {
            case PLAYER_POINTS -> playerPointsConfig().isEnabled();
            case MONEY -> vaultConfig().isEnabled();
        };
    }

    public boolean isAvailable(QuestResetPaymentType type) {
        if (!isEnabled(type)) {
            return false;
        }
        return switch (type) {
            case PLAYER_POINTS -> playerPointsApi() != null;
            case MONEY -> economy() != null;
        };
    }

    public boolean charge(Player player, QuestResetPaymentType type) {
        if (!isEnabled(type)) {
            return false;
        }
        return switch (type) {
            case PLAYER_POINTS -> chargePoints(player);
            case MONEY -> chargeMoney(player);
        };
    }

    public String displayAmount(QuestResetPaymentType type) {
        return switch (type) {
            case PLAYER_POINTS -> Integer.toString(Math.max(0, playerPointsConfig().getQuestResetCost()));
            case MONEY -> formatMoney(Math.max(0.0D, vaultConfig().getQuestResetCost()));
        };
    }

    public String displayPaymentName(QuestResetPaymentType type) {
        return switch (type) {
            case PLAYER_POINTS -> playerPointsConfig().getDisplayName();
            case MONEY -> vaultConfig().getDisplayName();
        };
    }

    public Config.MenuButton button(QuestResetPaymentType type) {
        return switch (type) {
            case PLAYER_POINTS -> playerPointsConfig().getButton();
            case MONEY -> vaultConfig().getButton();
        };
    }

    private boolean chargePoints(Player player) {
        int amount = Math.max(0, playerPointsConfig().getQuestResetCost());
        if (amount <= 0) {
            return true;
        }
        PlayerPointsAPI api = playerPointsApi();
        if (api == null) {
            return false;
        }
        boolean charged = api.take(player.getUniqueId(), amount);
        if (!charged) {
            log.warn("PlayerPoints quest reset purchase failed for {} amount {}.", player.getUniqueId(), amount);
        }
        return charged;
    }

    private boolean chargeMoney(Player player) {
        double amount = Math.max(0.0D, vaultConfig().getQuestResetCost());
        if (amount <= 0.0D) {
            return true;
        }
        Economy economy = economy();
        if (economy == null) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            log.warn("Vault quest reset purchase failed for {} amount {}: {}", player.getUniqueId(), amount, response.errorMessage);
        }
        return response.transactionSuccess();
    }

    private PlayerPointsAPI playerPointsApi() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (!(plugin instanceof PlayerPoints playerPoints) || !plugin.isEnabled()) {
            return null;
        }
        return playerPoints.getAPI();
    }

    private Economy economy() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    private Config.PlayerPointsCurrency playerPointsConfig() {
        Config.Currencies currencies = configProvider.get().getCurrencies();
        return currencies == null || currencies.getPlayerPoints() == null ? new Config.PlayerPointsCurrency() : currencies.getPlayerPoints();
    }

    private Config.VaultCurrency vaultConfig() {
        Config.Currencies currencies = configProvider.get().getCurrencies();
        return currencies == null || currencies.getVault() == null ? new Config.VaultCurrency() : currencies.getVault();
    }

    private String formatMoney(double amount) {
        Economy economy = economy();
        if (economy == null) {
            return "$" + MONEY_FORMAT.get().format(amount);
        }
        return economy.format(amount);
    }
}
