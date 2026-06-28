package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.state.Reloadable;
import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.Enableable;
import gg.moonrise.moss.spring.SpringComponent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.QuestVariableSelector;
import gg.moonrise.quests.sdk.event.QuestProgressEvent;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.model.GlobalQuestContribution;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class GlobalQuestService implements Enableable, Reloadable, Disableable {

    private static final UUID GLOBAL_PLAYER_ID = new UUID(0L, 0L);
    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final GlobalQuestRepository repository;
    private final QuestProgressIndicatorService progressIndicatorService;

    private volatile DayOfWeek startDay = DayOfWeek.FRIDAY;
    private volatile LocalTime startTime = LocalTime.of(5, 0);
    private volatile GlobalQuestState activeState;
    private volatile ScheduledTask rolloverTask;

    @Override
    public void onEnable() {
        reload();
    }

    @Override
    public synchronized void reload() {
        Config.GlobalSchedule schedule = configProvider.get().getGlobalQuests().getSchedule();
        this.startDay = parseDay(schedule == null ? null : schedule.getDayOfWeek());
        this.startTime = parseTime(schedule == null ? null : schedule.getTime());
        repository.loadExpiredUnrewarded(LocalDateTime.now())
                .thenCompose(this::finalizeExpiredStates)
                .thenCompose(unused -> ensureActiveStateAsync())
                .exceptionally(throwable -> {
                    log.error("Failed to reload QuestsPlus global quest state.", throwable);
                    return null;
                });
        scheduleNextRollover();
    }

    @Override
    public void onDisable() {
        ScheduledTask task = rolloverTask;
        if (task != null) {
            task.cancel();
        }
    }

    public CompletableFuture<GlobalQuestState> activeStateAsync() {
        GlobalQuestState cached = activeState;
        Period period = currentPeriod();
        if (cached != null && cached.periodKey().equals(period.key())) {
            return CompletableFuture.completedFuture(cached);
        }
        return ensureActiveStateAsync();
    }

    public GlobalQuestState cachedActiveState() {
        GlobalQuestState cached = activeState;
        Period period = currentPeriod();
        return cached != null && cached.periodKey().equals(period.key()) ? cached : null;
    }

    void cachedActiveState(GlobalQuestState state) {
        this.activeState = state;
    }

    public CompletableFuture<GlobalQuestState> refreshActiveQuest() {
        Period period = currentPeriod();
        activeState = null;
        return repository.deletePeriod(period.key())
                .thenCompose(unused -> ensureActiveStateAsync())
                .thenApply(state -> {
                    if (state != null) {
                        broadcastStarted(state);
                    }
                    return state;
                });
    }

    public int menuSlot() {
        Config.GlobalQuestMenu menu = configProvider.get().getGlobalQuests().getMenu();
        return menu == null ? 15 : Math.max(0, Math.min(53, menu.getSlot()));
    }

    public String timeRemainingPlaceholder() {
        GlobalQuestState state = cachedActiveState();
        LocalDateTime end = state == null ? currentPeriod().endsAt() : state.endsAt();
        long remainingMillis = Duration.between(LocalDateTime.now(), end).toMillis();
        long ceilMinutes = remainingMillis <= 0 ? 0 : (remainingMillis + 59_999) / 60_000;
        if (ceilMinutes < 60) {
            return "Ends in " + QuestNumberFormatter.format(ceilMinutes) + "m";
        }
        long ceilHours = (ceilMinutes + 59) / 60;
        if (ceilHours < 24) {
            return "Ends in " + QuestNumberFormatter.format(ceilHours) + "h";
        }
        long ceilDays = (ceilHours + 23) / 24;
        return "Ends in " + QuestNumberFormatter.format(ceilDays) + "d";
    }

    public List<QuestProgressResult> progressMatching(Player player, QuestType type, int amount, Predicate<GeneratedQuest> matcher) {
        return progressMatching(player, type, amount, matcher, QuestProgressEvent.Cause.EVENT);
    }

    public List<QuestProgressResult> progressMatching(Player player, QuestType type, int amount, Predicate<GeneratedQuest> matcher, QuestProgressEvent.Cause cause) {
        if (amount <= 0) {
            return List.of();
        }
        GlobalQuestState state = cachedActiveState();
        if (state == null) {
            ensureActiveStateAsync();
            return List.of();
        }
        GeneratedQuest quest = state.quest();
        if (quest.completed() || !quest.type().equals(type) || !matcher.test(quest)) {
            return List.of();
        }

        int credited;
        int previousProgress;
        GeneratedQuest updated;
        Map<UUID, Integer> contributions;
        GlobalQuestState updatedState;
        synchronized (this) {
            GlobalQuestState current = cachedActiveState();
            if (current == null || current.quest().completed() || !current.quest().type().equals(type) || !matcher.test(current.quest())) {
                return List.of();
            }
            int approvedAmount = approveProgress(player, current.quest(), amount, cause);
            if (approvedAmount <= 0) {
                return List.of();
            }
            int remaining = current.quest().goalAmount() - current.quest().progress();
            credited = Math.min(approvedAmount, Math.max(0, remaining));
            if (credited <= 0) {
                return List.of();
            }
            previousProgress = current.quest().progress();
            int updatedProgress = Math.min(current.quest().goalAmount(), current.quest().progress() + credited);
            updated = current.quest().withProgress(updatedProgress, updatedProgress >= current.quest().goalAmount());
            contributions = new LinkedHashMap<>(current.contributions());
            contributions.merge(player.getUniqueId(), credited, Integer::sum);
            updatedState = current.withQuest(updated, contributions);
            activeState = updatedState;
        }
        progressIndicatorService.showGlobal(player, updatedState, previousProgress);

        repository.updateProgressAndContribution(updatedState, player.getUniqueId(), credited)
                .exceptionally(throwable -> {
                    log.error("Failed to persist QuestsPlus global quest progress for {}.", updated.instanceId(), throwable);
                    return null;
                });
        return List.of(new QuestProgressResult(updated, updated.completed()));
    }

    public List<QuestProgressResult> progressAdminGoal(Player player, QuestType type, int amount) {
        return progressMatching(player, type, amount, quest -> true, QuestProgressEvent.Cause.COMMAND);
    }

    private int approveProgress(Player player, GeneratedQuest quest, int amount, QuestProgressEvent.Cause cause) {
        QuestDefinition definition = globalDefinition(quest).orElse(null);
        if (definition == null) {
            log.warn("Skipping QuestsPlus global progress for generated quest {} because definition '{}' is not loaded.", quest.instanceId(), quest.definitionId());
            return 0;
        }

        QuestProgressEvent event = new QuestProgressEvent(player, definition, quest, cause == null ? QuestProgressEvent.Cause.UNKNOWN : cause, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }
        return event.amount();
    }

    private Optional<QuestDefinition> globalDefinition(GeneratedQuest quest) {
        return loadGlobalDefinitions().stream()
                .filter(definition -> definition.id().equals(quest.definitionId()))
                .findFirst();
    }

    public String replaceGlobalTokens(String template, GlobalQuestState state, Player viewer) {
        String output = template == null ? "" : template;
        GeneratedQuest quest = state.quest();
        Map<String, String> values = commonGlobalPlaceholders(state, viewer);
        values.putAll(definitionService.variablePlaceholders(quest));
        putQuestNumbers(values, quest, true);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    private CompletableFuture<GlobalQuestState> ensureActiveStateAsync() {
        Period period = currentPeriod();
        return repository.loadActive(period.key())
                .thenCompose(loaded -> {
                    if (loaded != null) {
                        activeState = loaded;
                        return CompletableFuture.completedFuture(loaded);
                    }
                    GlobalQuestState generated = generate(period);
                    if (generated == null) {
                        activeState = null;
                        return CompletableFuture.completedFuture(null);
                    }
                    activeState = generated;
                    return repository.save(generated).thenApply(unused -> generated);
                });
    }

    private GlobalQuestState generate(Period period) {
        List<QuestDefinition> definitions = loadGlobalDefinitions();
        Instant now = Instant.now();
        List<QuestDefinition> enabled = definitions.stream()
                .filter(QuestDefinition::enabled)
                .filter(definition -> definition.activeAt(now))
                .toList();
        if (enabled.isEmpty()) {
            log.warn("No enabled QuestsPlus global quest definitions are available.");
            return null;
        }
        QuestDefinition definition = enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
        Map<String, String> variables = resolveVariables(definition);
        GeneratedQuest quest = definitionService.handler(definition.type())
                .createGeneratedQuest(definition, GLOBAL_PLAYER_ID, period.key(), variables);
        return new GlobalQuestState(quest, period.key(), period.startsAt(), period.endsAt(), Map.of(), false);
    }

    private List<QuestDefinition> loadGlobalDefinitions() {
        List<QuestDefinition> loaded = new ArrayList<>();
        Map<String, Boolean> ids = new HashMap<>();
        List<Config.QuestDefinitionConfig> questConfigs = configProvider.get().getGlobalQuests().getQuestDefinitions();
        if (questConfigs == null) {
            questConfigs = List.of();
        }
        for (Config.QuestDefinitionConfig config : questConfigs) {
            String id = QuestNames.normalize(config.getId());
            if (id.isBlank() || ids.put(id, true) != null) {
                log.error("Skipping invalid QuestsPlus global quest definition '{}': duplicate or blank id", id);
                continue;
            }
            try {
                QuestType type = QuestType.of(config.getType());
                QuestDifficulty difficulty = definitionService.difficulty(config.getDifficulty());
                Map<String, String> selectorTypes = new LinkedHashMap<>();
                Map<String, List<String>> selectorValues = new LinkedHashMap<>();
                Map<String, Config.VariableConfig> variables = config.getVariables() == null ? Map.of() : config.getVariables();
                for (Map.Entry<String, Config.VariableConfig> entry : variables.entrySet()) {
                    String variableKey = QuestNames.normalize(entry.getKey());
                    Config.VariableConfig variable = entry.getValue();
                    String selectorType = variable.getSelector().trim().toUpperCase(Locale.ROOT);
                    definitionService.selector(selectorType);
                    selectorTypes.put(variableKey, selectorType);
                    selectorValues.put(variableKey, safeList(variable.getValues()));
                }
                Config.Rewards rewards = config.getRewards() == null ? new Config.Rewards() : config.getRewards();
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
                definitionService.handler(type).validateDefinition(definition);
                loaded.add(definition);
            } catch (RuntimeException exception) {
                log.error("Skipping invalid QuestsPlus global quest definition '{}': {}", id, exception.getMessage());
            }
        }
        return List.copyOf(loaded);
    }

    private Map<String, String> resolveVariables(QuestDefinition definition) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : definition.selectorTypes().entrySet()) {
            String key = entry.getKey();
            QuestVariableSelector selector = definitionService.selector(entry.getValue());
            resolved.put(key, selector.select(definition.selectorValues().get(key), ThreadLocalRandom.current()));
        }
        return Map.copyOf(resolved);
    }

    private CompletableFuture<Void> finalizeExpiredStates(List<GlobalQuestState> states) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (GlobalQuestState state : states) {
            future = future.thenCompose(unused -> finalizeState(state));
        }
        return future;
    }

    private CompletableFuture<Void> finalizeState(GlobalQuestState state) {
        Scheduler.sync().run(task -> Bukkit.broadcast(configProvider.get().getGlobalQuests().getMessages().getPeriodEnded().asComponent()));
        List<Config.GlobalRewardTierConfig> tiers = rewardTiersForProgress(state.quest());
        if (tiers.isEmpty()) {
            return repository.markRewardsExecuted(state.quest().instanceId()).thenApply(unused -> null);
        }
        return repository.loadRankedContributions(state.quest().instanceId())
                .thenCompose(contributions -> dispatchTierRewards(state, contributions, tiers)
                        .thenCompose(unused -> repository.markRewardsExecuted(state.quest().instanceId()))
                        .thenApply(unused -> null));
    }

    private CompletableFuture<Void> dispatchTierRewards(GlobalQuestState state, List<GlobalQuestContribution> contributions, List<Config.GlobalRewardTierConfig> tiers) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (GlobalQuestContribution contribution : contributions) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(contribution.playerId());
            String playerName = offlinePlayer.getName() == null ? contribution.playerId().toString() : offlinePlayer.getName();
            for (Config.GlobalRewardTierConfig tier : tiers) {
                if (!qualifies(contribution.rank(), contribution.participants(), tier.getPercentile())) {
                    continue;
                }
                futures.add(repository.insertRewardExecution(state.quest().instanceId(), contribution.playerId(), tier.getPercentile())
                        .thenAccept(inserted -> {
                            if (!inserted) {
                                return;
                            }
                            Scheduler.sync().run(task -> {
                                for (String command : safeList(tier.getCommands())) {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceRewardTokens(command, state, contribution, playerName));
                                }
                            });
                        }));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    List<Config.GlobalRewardTierConfig> rewardTiersForProgress(GeneratedQuest quest) {
        double completionPercent = completionPercent(quest);
        if (quest.completed() || completionPercent >= 100.0D) {
            return fullRewardTiers();
        }
        if (completionPercent >= reducedRewardMinimumPercent()) {
            return reducedRewardTiers();
        }
        return List.of();
    }

    private double reducedRewardMinimumPercent() {
        double configured = configProvider.get().getGlobalQuests().getReducedRewardMinimumPercent();
        return Math.max(0.0D, Math.min(100.0D, configured));
    }

    private double completionPercent(GeneratedQuest quest) {
        if (quest.goalAmount() <= 0) {
            return 0.0D;
        }
        return Math.min(100.0D, (quest.progress() * 100.0D) / quest.goalAmount());
    }

    private boolean qualifies(int rank, int participants, int percentile) {
        if (participants <= 0 || percentile <= 0) {
            return false;
        }
        int cutoff = (int) Math.ceil(participants * (Math.min(percentile, 100) / 100.0D));
        return rank <= Math.max(1, cutoff);
    }

    public List<Config.GlobalRewardTierConfig> fullRewardTiers() {
        List<Config.GlobalRewardTierConfig> configured = configProvider.get().getGlobalQuests().getRewardTiers();
        return sanitizeRewardTiers(configured);
    }

    public List<Config.GlobalRewardTierConfig> reducedRewardTiers() {
        List<Config.GlobalRewardTierConfig> configured = configProvider.get().getGlobalQuests().getReducedRewardTiers();
        return sanitizeRewardTiers(configured);
    }

    private List<Config.GlobalRewardTierConfig> sanitizeRewardTiers(List<Config.GlobalRewardTierConfig> configured) {
        if (configured == null) {
            return List.of();
        }
        return configured.stream()
                .filter(tier -> tier != null && tier.getPercentile() > 0)
                .sorted(Comparator.comparingInt(Config.GlobalRewardTierConfig::getPercentile))
                .toList();
    }

    private String replaceRewardTokens(String template, GlobalQuestState state, GlobalQuestContribution contribution, String playerName) {
        String output = template == null ? "" : template;
        Map<String, String> values = new LinkedHashMap<>(globalPlaceholders(state, contribution.playerId(), contribution.contribution(), contribution.rank(), contribution.participants(), false));
        values.put("player", playerName);
        values.put("uuid", contribution.playerId().toString());
        values.putAll(definitionService.variablePlaceholders(state.quest()));
        putQuestNumbers(values, state.quest(), false);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    private Map<String, String> commonGlobalPlaceholders(GlobalQuestState state, Player viewer) {
        int contribution = state.contribution(viewer.getUniqueId());
        Rank rank = rank(state, viewer.getUniqueId());
        Map<String, String> values = globalPlaceholders(state, viewer.getUniqueId(), contribution, rank.rank(), rank.participants(), true);
        values.put("player", viewer.getName());
        values.put("uuid", viewer.getUniqueId().toString());
        return values;
    }

    private Map<String, String> globalPlaceholders(GlobalQuestState state, UUID playerId, int contribution, int rank, int participants, boolean formatted) {
        GeneratedQuest quest = state.quest();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("quest_id", quest.definitionId());
        values.put("quest_display_name", stripMiniMessageFallback(quest.displayName()));
        putQuestNumbers(values, quest, formatted);
        values.put("global_progress", number(quest.progress(), formatted));
        values.put("global_goal_amount", number(quest.goalAmount(), formatted));
        values.put("global_percent", percent(quest.progress(), quest.goalAmount(), formatted));
        values.put("contribution", number(contribution, formatted));
        values.put("contribution_percent", percent(contribution, Math.max(1, quest.progress()), formatted));
        values.put("global_rank", rank <= 0 ? "-" : number(rank, formatted));
        values.put("global_participants", number(participants, formatted));
        values.put("global_time_remaining", timeRemainingPlaceholder());
        values.put("quest_type", quest.type().key());
        values.put("period_key", state.periodKey());
        return values;
    }

    private Rank rank(GlobalQuestState state, UUID playerId) {
        List<Map.Entry<UUID, Integer>> ranked = state.contributions().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed().thenComparing(entry -> entry.getKey().toString()))
                .toList();
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : ranked) {
            if (entry.getKey().equals(playerId)) {
                return new Rank(rank, ranked.size());
            }
            rank++;
        }
        return new Rank(0, ranked.size());
    }

    private String percent(int value, int max, boolean formatted) {
        if (max <= 0) {
            return "0";
        }
        return number(Math.min(100, (int) Math.floor((value * 100.0D) / max)), formatted);
    }

    private void putQuestNumbers(Map<String, String> values, GeneratedQuest quest, boolean formatted) {
        values.put("goal_amount", number(quest.goalAmount(), formatted));
        values.put("goal-amount", number(quest.goalAmount(), formatted));
        values.put("progress", number(quest.progress(), formatted));
    }

    private String number(int value, boolean formatted) {
        return formatted ? QuestNumberFormatter.format(value) : Integer.toString(value);
    }

    private void scheduleNextRollover() {
        ScheduledTask previous = rolloverTask;
        if (previous != null) {
            previous.cancel();
        }
        LocalDateTime next = currentPeriod().endsAt();
        Duration delay = Duration.between(LocalDateTime.now(), next);
        if (delay.isNegative() || delay.isZero()) {
            delay = Duration.ofSeconds(1);
        }
        rolloverTask = Scheduler.sync().runDelayed(task -> {
            repository.loadExpiredUnrewarded(LocalDateTime.now())
                    .thenCompose(this::finalizeExpiredStates)
                    .thenCompose(unused -> ensureActiveStateAsync())
                    .thenAccept(state -> {
                        if (state != null) {
                            broadcastStarted(state);
                        }
                    });
            scheduleNextRollover();
        }, delay);
        log.info("Scheduled next QuestsPlus global quest rollover for {}.", next);
    }

    private Period currentPeriod() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate date = now.toLocalDate();
        int delta = Math.floorMod(date.getDayOfWeek().getValue() - startDay.getValue(), 7);
        LocalDateTime start = date.minusDays(delta).atTime(startTime);
        if (now.isBefore(start)) {
            start = start.minusDays(7);
        }
        return new Period(start.toLocalDate().toString(), start, start.plusDays(7));
    }

    private DayOfWeek parseDay(String raw) {
        if (raw == null || raw.isBlank()) {
            return DayOfWeek.FRIDAY;
        }
        try {
            return DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid QuestsPlus global quest day '{}'. Falling back to FRIDAY.", raw);
            return DayOfWeek.FRIDAY;
        }
    }

    private LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw == null ? "" : raw);
        } catch (DateTimeParseException exception) {
            log.warn("Invalid QuestsPlus global quest time '{}'. Falling back to 05:00.", raw);
            return LocalTime.of(5, 0);
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private String stripMiniMessageFallback(String input) {
        return input == null ? "" : input.replaceAll("<[^>]+>", "").trim();
    }

    private void broadcastStarted(GlobalQuestState state) {
        Scheduler.sync().run(task -> Bukkit.broadcast(configProvider.get().getGlobalQuests().getMessages().getPeriodStarted().asComponent(
                Placeholder.unparsed("quest_display_name", stripMiniMessageFallback(state.quest().displayName())),
                Placeholder.unparsed("quest_id", state.quest().definitionId()),
                Placeholder.unparsed("quest_type", state.quest().type().key())
        )));
    }

    private record Period(String key, LocalDateTime startsAt, LocalDateTime endsAt) {
    }

    private record Rank(int rank, int participants) {
    }
}
