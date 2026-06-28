package gg.moonrise.quests.sdk;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Predicate;

/**
 * Runtime API exposed by QuestsPlus for external quest integrations.
 *
 * <p>External plugins should obtain this service from Bukkit's {@code ServicesManager}
 * after QuestsPlus has enabled.</p>
 */
public interface QuestApi {

    /**
     * Registers a goal handler and its Bukkit listener callbacks for an owning plugin.
     *
     * @param owner the plugin that owns the handler
     * @param handler the handler to register
     * @throws IllegalArgumentException when the owner or handler is invalid or the type is already registered
     */
    void registerGoalHandler(Plugin owner, GoalHandler handler) throws IllegalArgumentException;

    /**
     * Unregisters one goal handler owned by a plugin.
     *
     * @param owner the plugin that owns the handler
     * @param type the quest type to unregister
     */
    void unregisterGoalHandler(Plugin owner, QuestType type);

    /**
     * Unregisters all goal handlers, variable selectors, and currencies owned by a plugin.
     *
     * @param owner the plugin whose registrations should be removed
     */
    void unregisterAll(Plugin owner);

    /**
     * Registers a variable selector for an owning plugin.
     *
     * @param owner the plugin that owns the selector
     * @param selector the selector to register
     * @throws IllegalArgumentException when the selector type is already registered
     */
    void registerVariableSelector(Plugin owner, QuestVariableSelector selector) throws IllegalArgumentException;

    /**
     * Registers a currency provider for an owning plugin.
     *
     * @param owner the plugin that owns the currency
     * @param currency the currency provider to register
     * @throws IllegalArgumentException when the owner or currency is invalid or the key is already registered
     */
    void registerCurrency(Plugin owner, QuestCurrency currency) throws IllegalArgumentException;

    /**
     * Unregisters one currency provider owned by a plugin.
     *
     * @param owner the plugin that owns the currency
     * @param key the currency key to unregister
     */
    void unregisterCurrency(Plugin owner, QuestCurrencyKey key);

    /**
     * Returns all currently registered currency keys.
     *
     * @return registered currency keys
     */
    List<QuestCurrencyKey> registeredCurrencies();

    /**
     * Returns all currently registered quest types.
     *
     * @return registered quest types
     */
    List<QuestType> registeredTypes();

    /**
     * Applies progress to matching personal and global quests for a player.
     *
     * <p>QuestsPlus fires a cancellable
     * {@link gg.moonrise.quests.sdk.event.QuestProgressEvent} with cause
     * {@code API} before any matched generated quest is mutated.</p>
     *
     * @param player the player earning progress
     * @param type the quest type to progress
     * @param amount the positive progress amount
     * @param matcher a predicate that confirms the generated quest matches the external event
     * @return progress results from personal quests
     */
    List<QuestProgressResult> progressMatching(Player player, QuestType type, int amount, Predicate<GeneratedQuest> matcher);
}
