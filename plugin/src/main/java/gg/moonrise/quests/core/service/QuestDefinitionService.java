package gg.moonrise.quests.core.service;

import gg.moonrise.engine.state.Reloadable;
import gg.moonrise.moss.spring.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.config.GlobalQuestsFile;
import gg.moonrise.quests.config.MilestoneConfig;
import gg.moonrise.quests.config.QuestDefinitionConfig;
import gg.moonrise.quests.config.QuestDifficultyConfig;
import gg.moonrise.quests.config.Rewards;
import gg.moonrise.quests.config.VariableConfig;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.QuestVariableSelector;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.domain.model.QuestDifficulty;
import gg.moonrise.quests.domain.model.QuestMilestone;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.util.QuestPlaceholders;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestDefinitionService implements Reloadable {

    private static final String INTERNAL_OWNER = "QuestsPlus";
    private static final String DEFAULT_DIFFICULTY = "easy";

    private final ConfigProvider configProvider;
    private final List<QuestVariableSelector> selectors;

    private volatile DefinitionSnapshot snapshot = DefinitionSnapshot.EMPTY;
    private volatile Map<String, QuestVariableSelector> selectorsByType = Map.of();
    private volatile Map<QuestType, GoalHandler> handlersByType = Map.of();
    private volatile Map<QuestType, String> handlerOwnersByType = Map.of();

    /**
     * Registers the variable selectors. Definitions are first parsed by
     * {@link #registerBuiltInGoalHandlers(Collection)}: the built-in handlers depend on services that
     * depend on this one, so they are handed in after construction instead of being injected here.
     */
    @PostConstruct
    public void init() {
        Map<String, QuestVariableSelector> selectorMap = new HashMap<>();
        for (QuestVariableSelector selector : selectors) {
            selectorMap.put(selector.type().toUpperCase(Locale.ROOT), selector);
        }
        this.selectorsByType = Map.copyOf(selectorMap);
    }

    /**
     * Registers the plugin's own goal handlers and parses the definitions once with all of them known.
     */
    public synchronized void registerBuiltInGoalHandlers(Collection<? extends GoalHandler> goalHandlers) {
        Map<QuestType, GoalHandler> handlerMap = new LinkedHashMap<>(handlersByType);
        Map<QuestType, String> ownerMap = new LinkedHashMap<>(handlerOwnersByType);
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
        this.snapshot = DefinitionSnapshot.of(
                snapshot.revision() + 1,
                loadedDifficulties,
                loadDefinitions(loadedDifficulties),
                loadGlobalDefinitions(loadedDifficulties)
        );
    }

    /**
     * Increases every time definitions are re-parsed (config reload or handler/selector registration),
     * so callers can tell when results derived from earlier definitions are stale.
     */
    public long revision() {
        return snapshot.revision();
    }

    public List<QuestDefinition> enabledDefinitions() {
        Instant now = Instant.now();
        return snapshot.definitions().stream()
                .filter(QuestDefinition::enabled)
                .filter(definition -> definition.activeAt(now))
                .toList();
    }

    public List<QuestDefinition> definitions() {
        return snapshot.definitions();
    }

    public Optional<QuestDefinition> definition(String id) {
        String normalized = QuestNames.normalize(id);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.definitionsById().get(normalized));
    }

    public Optional<QuestDefinition> definition(GeneratedQuest quest) {
        if (quest == null) {
            return Optional.empty();
        }
        return definition(quest.definitionId());
    }

    public List<QuestDefinition> globalDefinitions() {
        return snapshot.globalDefinitions();
    }

    public Optional<QuestDefinition> globalDefinition(String id) {
        String normalized = QuestNames.normalize(id);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.globalDefinitionsById().get(normalized));
    }

    /**
     * Looks up a difficulty by id. An unknown id falls back to the default difficulty ({@code easy}), so callers
     * that need an exact match compare the returned id. The result is only empty before the first successful load,
     * when no difficulties are known at all.
     */
    public Optional<QuestDifficulty> difficulty(String id) {
        Map<String, QuestDifficulty> difficultiesById = snapshot.difficultiesById();
        QuestDifficulty difficulty = difficultiesById.get(QuestNames.normalize(id));
        return Optional.ofNullable(difficulty == null ? difficultiesById.get(DEFAULT_DIFFICULTY) : difficulty);
    }

    public List<QuestDifficulty> difficulties() {
        return List.copyOf(snapshot.difficultiesById().values());
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
                .sorted(Comparator.comparing(QuestType::key))
                .toList();
    }

    public Map<String, List<QuestType>> registeredTypesByOwner() {
        Map<String, List<QuestType>> grouped = new LinkedHashMap<>();
        List<QuestType> internal = handlerOwnersByType.entrySet().stream()
                .filter(entry -> INTERNAL_OWNER.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(QuestType::key))
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
                            .sorted(Comparator.comparing(QuestType::key))
                            .toList();
                    if (!types.isEmpty()) {
                        grouped.put(owner, types);
                    }
                });
        return Collections.unmodifiableMap(grouped);
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

    /**
     * Picks a value for every variable of {@code definition} with its configured selector.
     */
    public Map<String, String> resolveVariables(QuestDefinition definition) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : definition.selectorTypes().entrySet()) {
            String key = entry.getKey();
            QuestVariableSelector selector = selector(entry.getValue());
            resolved.put(key, selector.select(definition.selectorValues().get(key), ThreadLocalRandom.current()));
        }
        return Map.copyOf(resolved);
    }

    public Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        return handlerIfRegistered(quest.type())
                .map(handler -> handler.variablePlaceholders(quest))
                .orElseGet(() -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : quest.variables().entrySet()) {
                        values.put(entry.getKey(), entry.getValue());
                        values.put(QuestPlaceholders.placeholderKey(entry.getKey()), entry.getValue());
                    }
                    return values;
                });
    }

    private Map<String, QuestDifficulty> loadDifficulties() {
        Map<String, QuestDifficulty> loaded = new LinkedHashMap<>();
        Map<String, QuestDifficultyConfig> configured = configProvider.get().getQuestDifficulties();
        if (configured == null || configured.isEmpty()) {
            configured = Map.of(DEFAULT_DIFFICULTY, new QuestDifficultyConfig());
        }

        for (Map.Entry<String, QuestDifficultyConfig> entry : configured.entrySet()) {
            String id = QuestNames.normalize(entry.getKey());
            if (id.isBlank()) {
                throw new IllegalArgumentException("Quest difficulty id cannot be blank");
            }
            if (loaded.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate quest difficulty id after normalization: " + id);
            }

            QuestDifficultyConfig config = entry.getValue() == null ? new QuestDifficultyConfig() : entry.getValue();
            Rewards rewards = config.getRewards() == null ? new Rewards() : config.getRewards();
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

        if (!loaded.containsKey(DEFAULT_DIFFICULTY)) {
            throw new IllegalArgumentException("Quest difficulties must include the default difficulty id 'easy'");
        }
        return Collections.unmodifiableMap(loaded);
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

    private List<QuestMilestone> loadMilestones(String difficultyId, QuestDifficultyConfig config) {
        List<QuestMilestone> milestones = new ArrayList<>();
        Map<Integer, Boolean> completedValues = new HashMap<>();
        List<MilestoneConfig> configured = config.getMilestones() == null ? List.of() : config.getMilestones();
        for (MilestoneConfig milestoneConfig : configured) {
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
        List<QuestDefinition> loaded = loadDefinitions(configProvider.get().getQuestDefinitions(), DefinitionScope.PERSONAL, difficulties);
        log.info("Loaded {} QuestsPlus quest definitions and {} difficulties.", loaded.size(), difficulties.size());
        return loaded;
    }

    private List<QuestDefinition> loadGlobalDefinitions(Map<String, QuestDifficulty> difficulties) {
        GlobalQuestsFile globalQuests = configProvider.get().getGlobalQuests();
        return loadDefinitions(globalQuests == null ? null : globalQuests.getQuestDefinitions(), DefinitionScope.GLOBAL, difficulties);
    }

    /**
     * Parses definitions for one scope. Invalid definitions are logged and skipped; only a blank or
     * duplicate personal id aborts the whole reload, because quests already stored for it would become ambiguous.
     */
    private List<QuestDefinition> loadDefinitions(
            List<QuestDefinitionConfig> questConfigs,
            DefinitionScope scope,
            Map<String, QuestDifficulty> difficulties
    ) {
        List<QuestDefinition> loaded = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (QuestDefinitionConfig config : questConfigs == null ? List.<QuestDefinitionConfig>of() : questConfigs) {
            String id = QuestNames.normalize(config.getId());
            if (id.isBlank() || !ids.add(id)) {
                if (scope.strictIds()) {
                    throw new IllegalArgumentException(id.isBlank() ? "Quest definition id cannot be blank" : "Duplicate quest definition id: " + id);
                }
                log.error("Skipping invalid QuestsPlus {} '{}': duplicate or blank id", scope.label(), id);
                continue;
            }

            try {
                loaded.add(parseDefinition(id, config, scope, difficulties));
            } catch (RuntimeException exception) {
                log.error("Skipping invalid QuestsPlus {} '{}': {}", scope.label(), id, exception.getMessage());
            }
        }
        return List.copyOf(loaded);
    }

    private QuestDefinition parseDefinition(
            String id,
            QuestDefinitionConfig config,
            DefinitionScope scope,
            Map<String, QuestDifficulty> difficulties
    ) {
        QuestType type = QuestType.of(config.getType());
        QuestDifficulty difficulty = definitionDifficulty(id, config, scope, difficulties);

        Map<String, String> selectorTypes = new LinkedHashMap<>();
        Map<String, List<String>> selectorValues = new LinkedHashMap<>();
        Map<String, VariableConfig> variables = config.getVariables() == null ? Map.of() : config.getVariables();
        for (Map.Entry<String, VariableConfig> entry : variables.entrySet()) {
            String variableKey = QuestNames.normalize(entry.getKey());
            if (variableKey.isBlank()) {
                throw new IllegalArgumentException("Quest definition " + id + " has a blank variable key");
            }
            VariableConfig variable = entry.getValue();
            if (variable == null || variable.getSelector() == null || variable.getSelector().isBlank()) {
                throw new IllegalArgumentException("Quest definition " + id + " variable " + variableKey + " has a blank selector");
            }
            String selectorType = variable.getSelector().trim().toUpperCase(Locale.ROOT);
            selector(selectorType);
            selectorTypes.put(variableKey, selectorType);
            selectorValues.put(variableKey, safeList(variable.getValues()));
        }
        Rewards rewards = config.getRewards() == null ? new Rewards() : config.getRewards();

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
        return definition;
    }

    private QuestDifficulty definitionDifficulty(
            String id,
            QuestDefinitionConfig config,
            DefinitionScope scope,
            Map<String, QuestDifficulty> difficulties
    ) {
        String difficultyId = QuestNames.normalize(config.getDifficulty());
        QuestDifficulty difficulty = difficulties.get(difficultyId);
        if (difficulty != null) {
            return difficulty;
        }
        if (scope.defaultsUnknownDifficulty()) {
            return difficulties.get(DEFAULT_DIFFICULTY);
        }
        throw new IllegalArgumentException("Quest definition " + id + " references unknown difficulty " + difficultyId);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * How strictly definitions of one scope are parsed.
     *
     * @param label name used in log messages
     * @param strictIds whether a blank or duplicate id fails the reload instead of skipping the definition
     * @param defaultsUnknownDifficulty whether an unknown difficulty falls back to the default difficulty
     */
    private record DefinitionScope(String label, boolean strictIds, boolean defaultsUnknownDifficulty) {

        private static final DefinitionScope PERSONAL = new DefinitionScope("quest definition", true, false);
        private static final DefinitionScope GLOBAL = new DefinitionScope("global quest definition", false, true);
    }

    /**
     * Difficulties and definitions loaded by one reload, published together so readers never
     * observe definitions from one reload paired with difficulties from another.
     */
    private record DefinitionSnapshot(
            long revision,
            Map<String, QuestDifficulty> difficultiesById,
            List<QuestDefinition> definitions,
            Map<String, QuestDefinition> definitionsById,
            List<QuestDefinition> globalDefinitions,
            Map<String, QuestDefinition> globalDefinitionsById
    ) {

        private static final DefinitionSnapshot EMPTY = new DefinitionSnapshot(0L, Map.of(), List.of(), Map.of(), List.of(), Map.of());

        private static DefinitionSnapshot of(
                long revision,
                Map<String, QuestDifficulty> difficultiesById,
                List<QuestDefinition> definitions,
                List<QuestDefinition> globalDefinitions
        ) {
            return new DefinitionSnapshot(revision, difficultiesById, definitions, byId(definitions), globalDefinitions, byId(globalDefinitions));
        }

        private static Map<String, QuestDefinition> byId(List<QuestDefinition> definitions) {
            Map<String, QuestDefinition> byId = new HashMap<>();
            for (QuestDefinition definition : definitions) {
                byId.put(definition.id(), definition);
            }
            return Map.copyOf(byId);
        }
    }
}
