package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.Enableable;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.currency.PlayerPointsCurrency;
import gg.moonrise.quests.currency.VaultCurrency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.QuestApi;
import gg.moonrise.quests.sdk.QuestVariableSelector;
import gg.moonrise.quests.sdk.currency.QuestCurrencies;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import gg.moonrise.quests.sdk.event.QuestProgressEvent;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestApiService implements QuestApi, Enableable, Disableable {

    private final QuestsPlusPlugin plugin;
    private final QuestDefinitionService definitionService;
    private final QuestService questService;
    private final QuestResetService resetService;
    private final QuestResetPurchaseService resetPurchaseService;
    private final ConfigProvider configProvider;

    private final Map<Plugin, Map<QuestType, GoalHandler>> externalHandlers = new LinkedHashMap<>();
    private final Map<Plugin, Map<String, QuestVariableSelector>> externalSelectors = new LinkedHashMap<>();
    private final Map<Plugin, Map<QuestCurrencyKey, QuestCurrency>> externalCurrencies = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getServicesManager().register(QuestApi.class, this, plugin, ServicePriority.Normal);
        log.info("Registered QuestsPlus SDK API service.");

        registerBuiltInCurrencies();
    }

    @Override
    public void onDisable() {
        Set<Plugin> owners = new LinkedHashSet<>();
        owners.addAll(externalHandlers.keySet());
        owners.addAll(externalSelectors.keySet());
        owners.addAll(externalCurrencies.keySet());
        for (Plugin owner : List.copyOf(owners)) {
            unregisterAll(owner);
        }
        Bukkit.getServicesManager().unregister(QuestApi.class, this);
    }

    @Override
    public synchronized void registerGoalHandler(Plugin owner, GoalHandler handler) throws IllegalArgumentException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(handler, "handler");
        if (!owner.isEnabled()) {
            throw new IllegalArgumentException("Cannot register quest handler for disabled plugin " + owner.getName());
        }
        Map<QuestType, GoalHandler> ownerHandlers = externalHandlers.get(owner);
        if (ownerHandlers != null && ownerHandlers.containsKey(handler.type())) {
            throw new IllegalArgumentException("Plugin " + owner.getName() + " already registered handler " + handler.type().key());
        }

        definitionService.registerGoalHandler(owner.getName(), handler);
        Bukkit.getPluginManager().registerEvents(handler, owner);
        externalHandlers.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(handler.type(), handler);
        log.info("Registered external QuestsPlus goal handler {} from {}.", handler.type().key(), owner.getName());
    }

    @Override
    public synchronized void unregisterGoalHandler(Plugin owner, QuestType type) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Map<QuestType, GoalHandler> handlers = externalHandlers.get(owner);
        if (handlers == null) {
            return;
        }
        GoalHandler handler = handlers.remove(type);
        if (handler == null) {
            return;
        }
        HandlerList.unregisterAll(handler);
        definitionService.unregisterGoalHandler(type, handler);
        if (handlers.isEmpty()) {
            externalHandlers.remove(owner);
        }
        log.info("Unregistered external QuestsPlus goal handler {} from {}.", type.key(), owner.getName());
    }

    @Override
    public synchronized void unregisterAll(Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        Map<QuestType, GoalHandler> handlers = externalHandlers.remove(owner);
        if (handlers != null) {
            for (Map.Entry<QuestType, GoalHandler> entry : handlers.entrySet()) {
                HandlerList.unregisterAll(entry.getValue());
                definitionService.unregisterGoalHandler(entry.getKey(), entry.getValue());
            }
        }

        Map<String, QuestVariableSelector> selectors = externalSelectors.remove(owner);
        if (selectors != null) {
            for (Map.Entry<String, QuestVariableSelector> entry : selectors.entrySet()) {
                definitionService.unregisterVariableSelector(entry.getKey(), entry.getValue());
            }
        }

        Map<QuestCurrencyKey, QuestCurrency> currencies = externalCurrencies.remove(owner);
        if (currencies != null) {
            for (QuestCurrencyKey key : currencies.keySet()) {
                resetPurchaseService.unregisterCurrency(owner, key);
            }
        }
    }

    @Override
    public synchronized void registerVariableSelector(Plugin owner, QuestVariableSelector selector) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(selector, "selector");
        if (!owner.isEnabled()) {
            throw new IllegalArgumentException("Cannot register quest variable selector for disabled plugin " + owner.getName());
        }
        definitionService.registerVariableSelector(selector);
        externalSelectors.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(selector.type().toUpperCase(java.util.Locale.ROOT), selector);
        log.info("Registered external QuestsPlus variable selector {} from {}.", selector.type(), owner.getName());
    }

    @Override
    public synchronized void registerCurrency(Plugin owner, QuestCurrency currency) throws IllegalArgumentException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        if (!owner.isEnabled()) {
            throw new IllegalArgumentException("Cannot register quest currency for disabled plugin " + owner.getName());
        }
        QuestCurrencyKey key = Objects.requireNonNull(currency.key(), "currency key");
        Map<QuestCurrencyKey, QuestCurrency> ownerCurrencies = externalCurrencies.get(owner);
        if (ownerCurrencies != null && ownerCurrencies.containsKey(key)) {
            throw new IllegalArgumentException("Plugin " + owner.getName() + " already registered currency " + key.key());
        }

        resetPurchaseService.registerCurrency(owner, currency);
        externalCurrencies.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(key, currency);
        log.info("Registered external QuestsPlus currency {} from {}.", key.key(), owner.getName());
    }

    @Override
    public synchronized void unregisterCurrency(Plugin owner, QuestCurrencyKey key) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        Map<QuestCurrencyKey, QuestCurrency> currencies = externalCurrencies.get(owner);
        if (currencies == null) {
            return;
        }
        QuestCurrency removed = currencies.remove(key);
        if (removed == null) {
            return;
        }
        resetPurchaseService.unregisterCurrency(owner, key);
        if (currencies.isEmpty()) {
            externalCurrencies.remove(owner);
        }
        log.info("Unregistered external QuestsPlus currency {} from {}.", key.key(), owner.getName());
    }

    @Override
    public List<QuestCurrencyKey> registeredCurrencies() {
        return resetPurchaseService.registeredCurrencyKeys();
    }

    @Override
    public List<QuestType> registeredTypes() {
        return definitionService.registeredTypes();
    }

    @Override
    public List<QuestProgressResult> progressMatching(Player player, QuestType type, int amount, Predicate<GeneratedQuest> matcher) {
        if (player == null || type == null || matcher == null) {
            return List.of();
        }
        return new ArrayList<>(questService.progressMatching(player, type, resetService.currentResetKey(), amount, matcher, QuestProgressEvent.Cause.API));
    }

    private void registerBuiltInCurrencies() {
        Set<QuestCurrencyKey> enabledCurrencies = enabledCurrencies();
        if (enabledCurrencies.contains(QuestCurrencies.VAULT)) {
            registerBuiltInCurrency(new VaultCurrency(configProvider));
        }
        if (enabledCurrencies.contains(QuestCurrencies.PLAYER_POINTS)) {
            registerBuiltInCurrency(new PlayerPointsCurrency(configProvider));
        }
    }

    private void registerBuiltInCurrency(QuestCurrency currency) {
        try {
            if (currency instanceof Enableable enableable) {
                enableable.onEnable();
            }
            resetPurchaseService.registerBuiltInCurrency(currency);
            if (!currency.isAvailable()) {
                log.error("Configured currency '{}' is enabled but unavailable; hiding it from the reset purchase menu until it is available.", currency.key().key());
            }
        } catch (RuntimeException exception) {
            log.error("Configured currency '{}' failed to initialize; hiding it from the reset purchase menu.", currency.key().key(), exception);
        }
    }

    private Set<QuestCurrencyKey> enabledCurrencies() {
        Config.Currencies currencies = configProvider.get().getCurrencies();
        List<String> configuredKeys = currencies == null || currencies.getEnabledCurrencies() == null
                ? List.of()
                : currencies.getEnabledCurrencies();
        Set<QuestCurrencyKey> enabled = new LinkedHashSet<>();
        for (String configuredKey : configuredKeys) {
            try {
                enabled.add(QuestCurrencyKey.of(configuredKey));
            } catch (IllegalArgumentException exception) {
                log.error("Ignoring invalid currency key '{}' in currencies.yml enabled-currencies.", configuredKey, exception);
            }
        }
        return enabled;
    }
}
