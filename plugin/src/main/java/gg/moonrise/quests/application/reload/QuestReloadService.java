package gg.moonrise.quests.application.reload;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.infrastructure.persistence.SqlProvider;
import gg.moonrise.quests.application.api.QuestApiService;
import gg.moonrise.quests.application.global.GlobalQuestService;
import gg.moonrise.quests.application.quest.QuestDefinitionService;
import gg.moonrise.quests.application.quest.QuestService;
import gg.moonrise.quests.application.reset.QuestResetService;
import gg.moonrise.quests.core.service.ConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Reloads the configuration together with storage and every service that caches config.
 */
@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestReloadService {

    private final ConfigProvider configProvider;
    private final SqlProvider sqlProvider;
    private final QuestDefinitionService definitionService;
    private final QuestResetService resetService;
    private final QuestApiService apiService;
    private final GlobalQuestService globalQuestService;
    private final QuestService questService;

    /**
     * Reads the new config without applying it, reconnects storage off the calling thread, and only applies the new
     * config (on the main thread) once storage is ready, so a failed reconnect changes nothing.
     */
    public CompletableFuture<ReloadOutcome> reload() {
        try {
            Config candidate = configProvider.loadFromDisk();
            CompletableFuture<ReloadOutcome> outcome = new CompletableFuture<>();
            sqlProvider.reload(candidate.getStorage()).whenComplete((result, error) -> Scheduler.sync().run(task ->
                    outcome.complete(applyAfterStorage(candidate, result, error))));
            return outcome;
        } catch (RuntimeException exception) {
            log.error("Failed to load QuestsPlus configuration.", exception);
            return CompletableFuture.completedFuture(new ReloadOutcome.Failed(exception));
        }
    }

    private ReloadOutcome applyAfterStorage(Config candidate, SqlProvider.ReloadResult result, Throwable error) {
        Throwable failure = error;
        if (result instanceof SqlProvider.ReloadResult.Failed failed) {
            failure = failed.cause();
        }
        if (failure != null) {
            log.error("Failed to reconnect QuestsPlus storage; the previous configuration is still active.", failure);
            return new ReloadOutcome.Failed(failure);
        }
        try {
            configProvider.apply(candidate);
            definitionService.reload();
            resetService.reload();
            apiService.reload();
            globalQuestService.reload();
            questService.claimRetroactiveMilestonesForOnlinePlayers();
            return new ReloadOutcome.Reloaded();
        } catch (RuntimeException exception) {
            log.error("Failed to reload QuestsPlus.", exception);
            return new ReloadOutcome.Failed(exception);
        }
    }

    /**
     * Outcome of {@link #reload()}.
     */
    public sealed interface ReloadOutcome {

        /**
         * The new configuration is active.
         */
        public record Reloaded() implements ReloadOutcome {
        }

        /**
         * The reload stopped; the previous configuration is still active unless applying it was what failed.
         */
        public record Failed(Throwable cause) implements ReloadOutcome {
        }
    }
}
