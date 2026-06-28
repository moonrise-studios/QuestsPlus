package gg.moonrise.quests.sdk.currency;

import org.bukkit.entity.Player;

/**
 * Currency provider that can be used for QuestsPlus purchases.
 *
 * <p>Implementations are owned by the plugin that registers them through
 * {@link gg.moonrise.quests.sdk.QuestApi#registerCurrency(org.bukkit.plugin.Plugin, QuestCurrency)}.
 * The owning plugin should keep any config, balance checks, and transaction logic inside
 * its own implementation.</p>
 */
public interface QuestCurrency {

    /**
     * Unique currency key.
     *
     * @return normalized currency key
     */
    QuestCurrencyKey key();

    /**
     * Display name used by QuestsPlus menus and messages.
     *
     * @return display name, MiniMessage supported
     */
    String displayName();

    /**
     * Display amount shown to a player before or after purchase.
     *
     * @param player the player viewing or buying the purchase
     * @return human-readable amount text
     */
    String displayAmount(Player player);

    /**
     * Configured Quest Reset purchase cost for this currency.
     *
     * @return non-negative quest reset cost
     */
    double questResetCost();

    /**
     * Button template used in the QuestsPlus purchase menu.
     *
     * @return purchase button template
     */
    QuestCurrencyButton button();

    /**
     * Charges the player for the purchase.
     *
     * <p>QuestsPlus only resets quests when this method returns {@code true}.</p>
     *
     * @param player player to charge
     * @param amount amount to charge
     * @return true when the transaction completed
     */
    boolean charge(Player player, double amount);

    boolean isAvailable();
}
