package gg.moonrise.quests.currency;

import gg.moonrise.moss.spring.Enableable;
import gg.moonrise.quests.config.currency.Currencies;
import gg.moonrise.quests.config.menu.MenuButton;
import gg.moonrise.quests.config.menu.MenuItem;
import gg.moonrise.quests.config.currency.PlayerPointsCurrencyConfig;
import gg.moonrise.quests.config.ConfigProvider;
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

import java.util.Optional;

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
        return Integer.toString(toPoints(questResetCost()));
    }

    @Override
    public double questResetCost() {
        return Math.max(0, config().getQuestResetCost());
    }

    @Override
    public QuestCurrencyButton button() {
        MenuButton button = config().getButton();
        MenuButton fallback = new PlayerPointsCurrencyConfig().getButton();
        MenuButton resolvedButton = button == null ? fallback : button;
        MenuItem item = resolvedButton.getItem() == null ? fallback.getItem() : resolvedButton.getItem();
        return QuestCurrencyButton.of(resolvedButton.getSlot(), item.getMaterial(), item.getName(), item.getLore());
    }

    @Override
    public boolean charge(Player player, double amount) {
        if (points == null) {
            log.warn("PlayerPoints currency charge rejected for {} because PlayerPoints is unavailable.", player.getUniqueId());
            return false;
        }
        int roundedAmount = toPoints(amount);
        if (roundedAmount <= 0) {
            return true;
        }

        return points.take(player.getUniqueId(), roundedAmount);
    }

    @Override
    public boolean refund(Player player, double amount) {
        if (points == null) {
            log.warn("PlayerPoints currency refund rejected for {} because PlayerPoints is unavailable.", player.getUniqueId());
            return false;
        }
        int roundedAmount = toPoints(amount);
        if (roundedAmount <= 0) {
            return true;
        }

        return points.give(player.getUniqueId(), roundedAmount);
    }

    @Override
    public boolean isAvailable() {
        return points != null || resolvePoints().isPresent();
    }

    @Override
    public void onEnable() {
        resolvePoints();
    }

    /**
     * Converts a currency amount to whole PlayerPoints. Display, charge and refund all use this
     * so the amount shown to a player is exactly the amount taken or given back.
     */
    public static int toPoints(double amount) {
        if (!(amount > 0.0D)) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(amount));
    }

    private Optional<PlayerPointsAPI> resolvePoints() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (!(plugin instanceof PlayerPoints playerPoints) || !plugin.isEnabled()) {
            return Optional.empty();
        }
        points = playerPoints.getAPI();
        return Optional.ofNullable(points);
    }

    private PlayerPointsCurrencyConfig config() {
        Currencies currencies = configProvider.get().getCurrencies();
        return currencies == null || currencies.getPlayerPoints() == null ? new PlayerPointsCurrencyConfig() : currencies.getPlayerPoints();
    }
}
