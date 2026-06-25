package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.Enableable;
import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.QuestApi;
import gg.moonrise.quests.sdk.QuestVariableSelector;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestApiService implements QuestApi, Enableable, Disableable {

    private final QuestsPlusPlugin plugin;
    private final QuestDefinitionService definitionService;
    private final QuestService questService;
    private final QuestResetService resetService;

    private final Map<Plugin, Map<QuestType, GoalHandler>> externalHandlers = new LinkedHashMap<>();
    private final Map<Plugin, Map<String, QuestVariableSelector>> externalSelectors = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getServicesManager().register(QuestApi.class, this, plugin, ServicePriority.Normal);
        log.info("Registered QuestsPlus SDK API service.");
    }

    @Override
    public void onDisable() {
        for (Plugin owner : List.copyOf(externalHandlers.keySet())) {
            unregisterAll(owner);
        }
        Bukkit.getServicesManager().unregister(QuestApi.class, this);
    }

    @Override
    public synchronized void registerGoalHandler(Plugin owner, GoalHandler handler) {
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
}
