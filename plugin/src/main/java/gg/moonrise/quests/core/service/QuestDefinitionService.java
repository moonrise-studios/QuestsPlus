package gg.moonrise.quests.core.service;

import gg.moonrise.engine.state.Reloadable;
import gg.moonrise.moss.spring.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.QuestVariableSelector;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestMilestone;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestDefinitionService implements Reloadable {

    private static final String INTERNAL_OWNER = "QuestsPlus";

    private final ConfigProvider configProvider;
    private final List<QuestVariableSelector> selectors;
    private final List<GoalHandler> goalHandlers;

    private volatile List<QuestDefinition> definitions = List.of();
    private volatile Map<String, QuestDifficulty> difficultiesById = Map.of();
    private volatile Map<String, QuestVariableSelector> selectorsByType = Map.of();
    private volatile Map<QuestType, GoalHandler> handlersByType = Map.of();
    private volatile Map<QuestType, String> handlerOwnersByType = Map.of();

    @PostConstruct
    public void init() {
        Map<String, QuestVariableSelector> selectorMap = new HashMap<>();
        for (QuestVariableSelector selector : selectors) {
            selectorMap.put(selector.type().toUpperCase(Locale.ROOT), selector);
        }
        this.selectorsByType = Map.copyOf(selectorMap);

        Map<QuestType, GoalHandler> handlerMap = new LinkedHashMap<>();
        Map<QuestType, String> ownerMap = new LinkedHashMap<>();
        for (GoalHandler handler : goalHandlers) {
            handlerMap.put(handler.type(), handler);
            ownerMap.put(handler.type(), INTERNAL_OWNER);
        }
        this.handlersByType = Map.copyOf(handlerMap);
        this.handlerOwnersByType = Map.copyOf(ownerMap);
        reload();
    }

    @Override
    public synchronized void reload() {
        Map<String, QuestDifficulty> loadedDifficulties = loadDifficulties();
        this.difficultiesById = loadedDifficulties;
        this.definitions = loadDefinitions(loadedDifficulties);
    }

    public List<QuestDefinition> enabledDefinitions() {
        Instant now = Instant.now();
        return definitions.stream()
                .filter(QuestDefinition::enabled)
                .filter(definition -> definition.activeAt(now))
                .toList();
    }

    public List<QuestDefinition> definitions() {
        return definitions;
    }

    public Optional<QuestDefinition> definition(String id) {
        String normalized = QuestNames.normalize(id);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return definitions.stream()
                .filter(definition -> definition.id().equals(normalized))
                .findFirst();
    }

    public Optional<QuestDefinition> definition(GeneratedQuest quest) {
        if (quest == null) {
            return Optional.empty();
        }
        return definition(quest.definitionId());
    }

    public QuestDifficulty difficulty(String id) {
        String normalized = QuestNames.normalize(id);
        QuestDifficulty difficulty = difficultiesById.get(normalized);
        if (difficulty == null) {
            return difficultiesById.get("easy");
        }
        return difficulty;
    }

    public List<QuestDifficulty> difficulties() {
        return List.copyOf(difficultiesById.values());
    }

    public QuestVariableSelector selector(String type) {
        QuestVariableSelector selector = selectorsByType.get(type.toUpperCase(Locale.ROOT));
        if (selector == null) {
            throw new IllegalArgumentException("Unknown quest variable selector: " + type);
        }
        return selector;
    }

    public GoalHandler handler(QuestType type) {
        GoalHandler handler = handlersByType.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No goal handler registered for " + type);
        }
        return handler;
    }

    public Optional<GoalHandler> handlerIfRegistered(QuestType type) {
        return Optional.ofNullable(handlersByType.get(type));
    }

    public List<QuestType> registeredTypes() {
        return handlersByType.keySet().stream()
                .sorted(java.util.Comparator.comparing(QuestType::key))
                .toList();
    }

    public Map<String, List<QuestType>> registeredTypesByOwner() {
        Map<String, List<QuestType>> grouped = new LinkedHashMap<>();
        List<QuestType> internal = handlerOwnersByType.entrySet().stream()
                .filter(entry -> INTERNAL_OWNER.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted(java.util.Comparator.comparing(QuestType::key))
                .toList();
        if (!internal.isEmpty()) {
            grouped.put(INTERNAL_OWNER, internal);
        }

        handlerOwnersByType.values().stream()
                .filter(owner -> !INTERNAL_OWNER.equals(owner))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(owner -> {
                    List<QuestType> types = handlerOwnersByType.entrySet().stream()
                            .filter(entry -> owner.equals(entry.getValue()))
                            .map(Map.Entry::getKey)
                            .sorted(java.util.Comparator.comparing(QuestType::key))
                            .toList();
                    if (!types.isEmpty()) {
                        grouped.put(owner, types);
                    }
                });
        return java.util.Collections.unmodifiableMap(grouped);
    }

    public synchronized void registerGoalHandler(GoalHandler handler) {
        registerGoalHandler(INTERNAL_OWNER, handler);
    }

    public synchronized void registerGoalHandler(String owner, GoalHandler handler) {
        QuestType type = handler.type();
        Map<QuestType, GoalHandler> updated = new LinkedHashMap<>(handlersByType);
        GoalHandler existing = updated.get(type);
        if (existing != null && existing != handler) {
            throw new IllegalArgumentException("A goal handler is already registered for " + type.key());
        }
        updated.put(type, handler);
        Map<QuestType, String> updatedOwners = new LinkedHashMap<>(handlerOwnersByType);
        updatedOwners.put(type, owner == null || owner.isBlank() ? INTERNAL_OWNER : owner);
        handlersByType = Map.copyOf(updated);
        handlerOwnersByType = Map.copyOf(updatedOwners);
        reload();
    }

    public synchronized void unregisterGoalHandler(QuestType type, GoalHandler handler) {
        GoalHandler existing = handlersByType.get(type);
        if (existing != handler) {
            return;
        }
        Map<QuestType, GoalHandler> updated = new LinkedHashMap<>(handlersByType);
        updated.remove(type);
        Map<QuestType, String> updatedOwners = new LinkedHashMap<>(handlerOwnersByType);
        updatedOwners.remove(type);
        handlersByType = Map.copyOf(updated);
        handlerOwnersByType = Map.copyOf(updatedOwners);
        reload();
    }

    public synchronized void registerVariableSelector(QuestVariableSelector selector) {
        String type = selector.type().toUpperCase(Locale.ROOT);
        Map<String, QuestVariableSelector> updated = new LinkedHashMap<>(selectorsByType);
        QuestVariableSelector existing = updated.get(type);
        if (existing != null && existing != selector) {
            throw new IllegalArgumentException("A quest variable selector is already registered for " + type);
        }
        updated.put(type, selector);
        selectorsByType = Map.copyOf(updated);
        reload();
    }

    public synchronized void unregisterVariableSelector(String selectorType, QuestVariableSelector selector) {
        String type = selectorType.toUpperCase(Locale.ROOT);
        QuestVariableSelector existing = selectorsByType.get(type);
        if (existing != selector) {
            return;
        }
        Map<String, QuestVariableSelector> updated = new LinkedHashMap<>(selectorsByType);
        updated.remove(type);
        selectorsByType = Map.copyOf(updated);
        reload();
    }

    public Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        return handlerIfRegistered(quest.type())
                .map(handler -> handler.variablePlaceholders(quest))
                .orElseGet(() -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : quest.variables().entrySet()) {
                        values.put(entry.getKey(), entry.getValue());
                        values.put(QuestNames.placeholderKey(entry.getKey()), entry.getValue());
                    }
                    return values;
                });
    }

    private Map<String, QuestDifficulty> loadDifficulties() {
        Map<String, QuestDifficulty> loaded = new LinkedHashMap<>();
        Map<String, Config.QuestDifficultyConfig> configured = configProvider.get().getQuestDifficulties();
        if (configured == null || configured.isEmpty()) {
            configured = Map.of("easy", new Config.QuestDifficultyConfig());
        }

        for (Map.Entry<String, Config.QuestDifficultyConfig> entry : configured.entrySet()) {
            String id = QuestNames.normalize(entry.getKey());
            if (id.isBlank()) {
                throw new IllegalArgumentException("Quest difficulty id cannot be blank");
            }
            if (loaded.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate quest difficulty id after normalization: " + id);
            }

            Config.QuestDifficultyConfig config = entry.getValue() == null ? new Config.QuestDifficultyConfig() : entry.getValue();
            Config.Rewards rewards = config.getRewards() == null ? new Config.Rewards() : config.getRewards();
            List<QuestMilestone> milestones = loadMilestones(id, config);
            Map<String, Integer> requirements = loadRequirements(id, config.getRequirements());
            loaded.put(id, new QuestDifficulty(
                    id,
                    config.getDisplayName() == null ? id : config.getDisplayName(),
                    config.getPickerSlot(),
                    config.getMilestonesSlot(),
                    requirements,
                    safeList(rewards.getCommands()),
                    safeList(config.getLore()),
                    milestones
            ));
        }

        if (!loaded.containsKey("easy")) {
            throw new IllegalArgumentException("Quest difficulties must include the default difficulty id 'easy'");
        }
        return Map.copyOf(loaded);
    }

    private Map<String, Integer> loadRequirements(String difficultyId, Map<String, Integer> configured) {
        if (configured == null || configured.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> requirements = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : configured.entrySet()) {
            String requiredDifficultyId = QuestNames.normalize(entry.getKey());
            if (requiredDifficultyId.isBlank()) {
                throw new IllegalArgumentException("Quest difficulty " + difficultyId + " has a blank requirement difficulty id");
            }
            int requiredCompletions = entry.getValue() == null ? 0 : entry.getValue();
            if (requiredCompletions < 0) {
                throw new IllegalArgumentException("Quest difficulty " + difficultyId + " has a negative requirement for " + requiredDifficultyId);
            }
            if (requiredCompletions == 0) {
                continue;
            }
            if (requirements.put(requiredDifficultyId, requiredCompletions) != null) {
                throw new IllegalArgumentException("Quest difficulty " + difficultyId + " has duplicate requirement difficulty id after normalization: " + requiredDifficultyId);
            }
        }
        return Collections.unmodifiableMap(requirements);
    }

    private List<QuestMilestone> loadMilestones(String difficultyId, Config.QuestDifficultyConfig config) {
        List<QuestMilestone> milestones = new ArrayList<>();
        Map<Integer, Boolean> completedValues = new HashMap<>();
        List<Config.MilestoneConfig> configured = config.getMilestones() == null ? List.of() : config.getMilestones();
        for (Config.MilestoneConfig milestoneConfig : configured) {
            int completed = milestoneConfig.getCompleted();
            if (completed <= 0) {
                throw new IllegalArgumentException("Quest difficulty " + difficultyId + " has non-positive milestone completed value " + completed);
            }
            if (completedValues.put(completed, true) != null) {
                throw new IllegalArgumentException("Quest difficulty " + difficultyId + " has duplicate milestone completed value " + completed);
            }
            milestones.add(new QuestMilestone(
                    difficultyId,
                    completed,
                    milestoneConfig.getDisplayName() == null ? QuestNumberFormatter.format(completed) : milestoneConfig.getDisplayName(),
                    safeList(milestoneConfig.getLore()),
                    safeList(milestoneConfig.getCommands())
            ));
        }
        return List.copyOf(milestones);
    }

    private List<QuestDefinition> loadDefinitions(Map<String, QuestDifficulty> difficulties) {
        List<QuestDefinition> loaded = new ArrayList<>();
        Map<String, Boolean> ids = new HashMap<>();
        List<Config.QuestDefinitionConfig> questConfigs = configProvider.get().getQuestDefinitions();
        if (questConfigs == null) {
            questConfigs = List.of();
        }
        for (Config.QuestDefinitionConfig config : questConfigs) {
            String id = QuestNames.normalize(config.getId());
            if (id.isBlank()) {
                throw new IllegalArgumentException("Quest definition id cannot be blank");
            }
            if (ids.put(id, true) != null) {
                throw new IllegalArgumentException("Duplicate quest definition id: " + id);
            }

            QuestType type;
            try {
                type = QuestType.of(config.getType());
            } catch (IllegalArgumentException exception) {
                log.error("Skipping invalid QuestsPlus quest definition '{}': {}", id, exception.getMessage());
                continue;
            }
            String difficultyId = QuestNames.normalize(config.getDifficulty());
            QuestDifficulty difficulty = difficulties.get(difficultyId);
            if (difficulty == null) {
                throw new IllegalArgumentException("Quest definition " + id + " references unknown difficulty " + difficultyId);
            }

            Map<String, String> selectorTypes = new LinkedHashMap<>();
            Map<String, List<String>> selectorValues = new LinkedHashMap<>();
            Map<String, Config.VariableConfig> variables = config.getVariables() == null ? Map.of() : config.getVariables();
            for (Map.Entry<String, Config.VariableConfig> entry : variables.entrySet()) {
                String variableKey = QuestNames.normalize(entry.getKey());
                Config.VariableConfig variable = entry.getValue();
                String selectorType = variable.getSelector().trim().toUpperCase(Locale.ROOT);
                selector(selectorType);
                selectorTypes.put(variableKey, selectorType);
                selectorValues.put(variableKey, safeList(variable.getValues()));
            }
            Config.Rewards rewards = config.getRewards() == null ? new Config.Rewards() : config.getRewards();

            try {
                QuestDefinition definition = new QuestDefinition(
                        id,
                        type,
                        config.isEnabled(),
                        difficulty.id(),
                        difficulty.displayName(),
                        config.getDisplayName(),
                        safeList(config.getDescription()),
                        Map.copyOf(selectorTypes),
                        Map.copyOf(selectorValues),
                        QuestDefinitionSchedules.parse(config.getSchedule(), id),
                        safeList(rewards.getCommands())
                );
                handler(type).validateDefinition(definition);
                loaded.add(definition);
            } catch (IllegalArgumentException exception) {
                log.error("Skipping invalid QuestsPlus quest definition '{}': {}", id, exception.getMessage());
                continue;
            }
        }
        log.info("Loaded {} QuestsPlus quest definitions and {} difficulties.", loaded.size(), difficulties.size());
        return List.copyOf(loaded);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
