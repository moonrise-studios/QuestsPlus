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
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

@Slf4j(topic = "QuestsPlus")
@RequiredArgsConstructor
public class PlayerPointsCurrency implements QuestCurrency, Enableable {

    private final ConfigProvider configProvider;
    private PlayerPointsAPI points;

    @Override
    public QuestCurrencyKey key() {
        return QuestCurrencies.PLAYER_POINTS;
    }

    @Override
    public String displayName() {
        return config().getDisplayName();
    }

    @Override
    public String displayAmount(Player player) {
        return Integer.toString((int) questResetCost());
    }

    @Override
    public double questResetCost() {
        return Math.max(0, config().getQuestResetCost());
    }

    @Override
    public QuestCurrencyButton button() {
        Config.MenuButton button = config().getButton();
        Config.MenuButton fallback = new Config.PlayerPointsCurrency().getButton();
        Config.MenuButton resolvedButton = button == null ? fallback : button;
        Config.MenuItem item = resolvedButton.getItem() == null ? fallback.getItem() : resolvedButton.getItem();
        return QuestCurrencyButton.of(resolvedButton.getSlot(), item.getMaterial(), item.getName(), item.getLore());
    }

    @Override
    public boolean charge(Player player, double amount) {
        if (points == null) {
            log.warn("PlayerPoints currency charge rejected for {} because PlayerPoints is unavailable.", player.getUniqueId());
            return false;
        }
        int roundedAmount = (int) Math.max(0, Math.round(amount));
        if (roundedAmount <= 0) {
            return true;
        }

        return points.take(player.getUniqueId(), roundedAmount);
    }

    @Override
    public boolean isAvailable() {
        return points != null || resolvePoints() != null;
    }

    @Override
    public void onEnable() {
        resolvePoints();
    }

    private PlayerPointsAPI resolvePoints() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (!(plugin instanceof PlayerPoints playerPoints) || !plugin.isEnabled()) {
            return null;
        }
        points = playerPoints.getAPI();
        return points;
    }

    private Config.PlayerPointsCurrency config() {
        Config.Currencies currencies = configProvider.get().getCurrencies();
        return currencies == null || currencies.getPlayerPoints() == null ? new Config.PlayerPointsCurrency() : currencies.getPlayerPoints();
    }
}
