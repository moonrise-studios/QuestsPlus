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
import gg.moonrise.quests.sdk.event.QuestProgressEvent;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.model.GlobalQuestContribution;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.util.QuestNumberFormatter;
import gg.moonrise.quests.util.QuestSchedules;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final QuestClock clock;

    private volatile DayOfWeek startDay = QuestSchedules.DEFAULT_DAY;
    private volatile LocalTime startTime = QuestSchedules.DEFAULT_TIME;
    /*
     * Every write to activeState, activeStateLoad, unavailablePeriod and stateGeneration happens while
     * holding stateLock; the volatile fields can still be read without it. stateGeneration increases
     * whenever the cached state is replaced or invalidated, so a load that started earlier can tell
     * that its result is stale and must not be published.
     */
    private final Object stateLock = new Object();
    private volatile GlobalQuestState activeState;
    private volatile CompletableFuture<GlobalQuestState> activeStateLoad;
    private volatile UnavailablePeriod unavailablePeriod;
    private long stateGeneration;
    private volatile ScheduledTask rolloverTask;

    @Override
    public void onEnable() {
        reload();
    }

    @Override
    public synchronized void reload() {
        Config.GlobalSchedule schedule = configProvider.get().getGlobalQuests().getSchedule();
        this.startDay = QuestSchedules.parseDay(schedule == null ? null : schedule.getDayOfWeek(), "global quest");
        this.startTime = QuestSchedules.parseTime(schedule == null ? null : schedule.getTime(), "global quest");
        synchronized (stateLock) {
            this.unavailablePeriod = null;
        }
        repository.loadExpiredUnrewarded(clock.now().toLocalDateTime())
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

    public void cachedActiveState(GlobalQuestState state) {
        synchronized (stateLock) {
            stateGeneration++;
            activeState = state;
            activeStateLoad = null;
        }
    }

    public CompletableFuture<GlobalQuestState> refreshActiveQuest() {
        Period period = currentPeriod();
        synchronized (stateLock) {
            // Loads and progress writes that started before the refresh see a new generation and are dropped.
            stateGeneration++;
            activeState = null;
            activeStateLoad = null;
            unavailablePeriod = null;
        }
        return repository.deletePeriod(period.key())
                .thenCompose(unused -> ensureActiveStateAsync())
                .thenApply(state -> {
                    if (state != null) {
                        broadcastStarted(state);
                    }
                    return state;
                });
    }

    public String timeRemainingPlaceholder() {
        GlobalQuestState state = cachedActiveState();
        LocalDateTime end = state == null ? currentPeriod().endsAt() : state.endsAt();
        return QuestSchedules.formatRemaining("Ends in ", QuestSchedules.until(clock.now(), end), true);
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

        // Listeners may call back into QuestsPlus from other threads, so the event is fired without
        // holding the state lock; the result is re-validated against the current state below.
        int approvedAmount = approveProgress(player, quest, amount, cause);
        if (approvedAmount <= 0) {
            return List.of();
        }

        int credited;
        int previousProgress;
        GeneratedQuest updated;
        Map<UUID, Integer> contributions;
        GlobalQuestState updatedState;
        synchronized (stateLock) {
            GlobalQuestState current = cachedActiveState();
            // The quest may have been refreshed, completed or advanced while listeners ran: only credit
            // the same quest instance, clamped against its current progress.
            if (current == null || !current.quest().instanceId().equals(quest.instanceId()) || current.quest().completed()) {
                return List.of();
            }
            int remaining = current.quest().goalAmount() - current.quest().progress();
            credited = Math.clamp(approvedAmount, 0, Math.max(0, remaining));
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
        QuestDefinition definition = definitionService.globalDefinition(quest.definitionId()).orElse(null);
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
        GlobalQuestState cached = activeState;
        if (cached != null && cached.periodKey().equals(period.key())) {
            return CompletableFuture.completedFuture(cached);
        }
        if (isUnavailable(period)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<GlobalQuestState> loading = activeStateLoad;
        if (loading != null) {
            return loading;
        }
        CompletableFuture<GlobalQuestState> created = new CompletableFuture<>();
        long generation;
        synchronized (stateLock) {
            cached = activeState;
            if (cached != null && cached.periodKey().equals(period.key())) {
                return CompletableFuture.completedFuture(cached);
            }
            loading = activeStateLoad;
            if (loading != null) {
                return loading;
            }
            activeStateLoad = created;
            generation = stateGeneration;
        }
        loadOrCreateActiveState(period, generation).whenComplete((state, throwable) -> {
            synchronized (stateLock) {
                if (activeStateLoad == created) {
                    activeStateLoad = null;
                }
            }
            if (throwable != null) {
                created.completeExceptionally(throwable);
            } else {
                created.complete(state);
            }
        });
        return created;
    }

    private CompletableFuture<GlobalQuestState> loadOrCreateActiveState(Period period, long generation) {
        return repository.loadActive(period.key())
                .thenCompose(loaded -> {
                    if (loaded != null) {
                        return CompletableFuture.completedFuture(publishLoadedState(generation, loaded));
                    }
                    long definitionsRevision = definitionService.revision();
                    Optional<GlobalQuestState> generated = generate(period);
                    if (generated.isEmpty()) {
                        markUnavailable(generation, period, definitionsRevision);
                        return CompletableFuture.completedFuture(null);
                    }
                    return repository.save(generated.get()).thenApply(saved -> publishLoadedState(generation, saved));
                });
    }

    /**
     * Publishes a state loaded by a load that started at {@code generation}, unless the cache was
     * refreshed or replaced in the meantime; then the newer cached state wins and is returned instead.
     */
    private GlobalQuestState publishLoadedState(long generation, GlobalQuestState loaded) {
        synchronized (stateLock) {
            if (generation != stateGeneration) {
                return cachedActiveState();
            }
            activeState = loaded;
            return loaded;
        }
    }

    /**
     * True when this period already found no global quest to generate and nothing that could change
     * that (reload, refresh, definition changes or a new period) has happened since.
     */
    private boolean isUnavailable(Period period) {
        UnavailablePeriod unavailable = unavailablePeriod;
        return unavailable != null
                && unavailable.periodKey().equals(period.key())
                && unavailable.definitionsRevision() == definitionService.revision();
    }

    private void markUnavailable(long generation, Period period, long definitionsRevision) {
        UnavailablePeriod previous;
        synchronized (stateLock) {
            if (generation != stateGeneration) {
                return;
            }
            previous = unavailablePeriod;
            unavailablePeriod = new UnavailablePeriod(period.key(), definitionsRevision);
        }
        if (previous == null || !previous.periodKey().equals(period.key())) {
            log.warn("No enabled QuestsPlus global quest definitions are available for period {}.", period.key());
        }
    }

    private Optional<GlobalQuestState> generate(Period period) {
        List<QuestDefinition> definitions = definitionService.globalDefinitions();
        Instant now = clock.instant();
        List<QuestDefinition> enabled = definitions.stream()
                .filter(QuestDefinition::enabled)
                .filter(definition -> definition.activeAt(now))
                .toList();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        QuestDefinition definition = enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
        Map<String, String> variables = definitionService.resolveVariables(definition);
        GeneratedQuest quest = definitionService.handler(definition.type())
                .createGeneratedQuest(definition, GLOBAL_PLAYER_ID, period.key(), variables);
        return Optional.of(new GlobalQuestState(quest, period.key(), period.startsAt(), period.endsAt(), Map.of(), false));
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
            for (Config.GlobalRewardTierConfig tier : tiers) {
                if (!qualifies(contribution.rank(), contribution.participants(), tier.getPercentile())) {
                    continue;
                }
                // The insert completes on the SQL thread; everything touching the Bukkit API runs on the global region.
                futures.add(repository.insertRewardExecution(state.quest().instanceId(), contribution.playerId(), tier.getPercentile())
                        .thenAccept(inserted -> {
                            if (inserted) {
                                Scheduler.sync().run(task -> dispatchTierCommands(state, contribution, tier));
                            }
                        }));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private void dispatchTierCommands(GlobalQuestState state, GlobalQuestContribution contribution, Config.GlobalRewardTierConfig tier) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(contribution.playerId());
        String playerName = offlinePlayer.getName() == null ? contribution.playerId().toString() : offlinePlayer.getName();
        for (String command : safeList(tier.getCommands())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceRewardTokens(command, state, contribution, playerName));
        }
    }

    public List<Config.GlobalRewardTierConfig> rewardTiersForProgress(GeneratedQuest quest) {
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
        return Math.clamp(configured, 0.0D, 100.0D);
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

    /**
     * Ranks by contribution (highest first, ties broken by UUID string) in one pass by counting the
     * participants ahead of the player instead of sorting every contribution.
     */
    private Rank rank(GlobalQuestState state, UUID playerId) {
        int own = state.contribution(playerId);
        String ownKey = playerId.toString();
        int participants = 0;
        int ahead = 0;
        for (Map.Entry<UUID, Integer> entry : state.contributions().entrySet()) {
            int value = entry.getValue();
            if (value <= 0) {
                continue;
            }
            participants++;
            if (own > 0 && (value > own || value == own && entry.getKey().toString().compareTo(ownKey) < 0)) {
                ahead++;
            }
        }
        return new Rank(own > 0 ? ahead + 1 : 0, participants);
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
        ZonedDateTime now = clock.now();
        Period period = periodAt(now);
        Duration delay = QuestSchedules.until(now, period.endsAt());
        if (delay.isNegative() || delay.isZero()) {
            delay = Duration.ofSeconds(1);
        }
        rolloverTask = Scheduler.sync().runDelayed(task -> onRolloverTaskFired(period.key()), delay);
        log.info("Scheduled next QuestsPlus global quest rollover for {}.", period.endsAt());
    }

    private void onRolloverTaskFired(String scheduledPeriodKey) {
        if (currentPeriod().key().equals(scheduledPeriodKey)) {
            // Tick timing or a clock adjustment fired the task before the period ended; wait for the real end.
            log.debug("QuestsPlus global rollover fired before period {} ended; rescheduling.", scheduledPeriodKey);
            scheduleNextRollover();
            return;
        }
        repository.loadExpiredUnrewarded(clock.now().toLocalDateTime())
                .thenCompose(this::finalizeExpiredStates)
                .thenCompose(unused -> ensureActiveStateAsync())
                .thenAccept(state -> {
                    if (state != null) {
                        broadcastStarted(state);
                    }
                });
        scheduleNextRollover();
    }

    private Period currentPeriod() {
        return periodAt(clock.now());
    }

    private Period periodAt(ZonedDateTime now) {
        LocalDateTime start = QuestSchedules.weeklyStart(now, startDay, startTime);
        return new Period(start.toLocalDate().toString(), start, start.plusDays(7));
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

    private record UnavailablePeriod(String periodKey, long definitionsRevision) {
    }
}
