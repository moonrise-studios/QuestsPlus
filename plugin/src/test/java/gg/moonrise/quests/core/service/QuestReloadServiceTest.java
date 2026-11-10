package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.SyncScheduler;
import gg.moonrise.quests.config.Config;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestReloadServiceTest {

    private ConfigProvider configProvider;
    private SqlProvider sqlProvider;
    private QuestDefinitionService definitionService;
    private QuestResetService resetService;
    private QuestApiService apiService;
    private GlobalQuestService globalQuestService;
    private QuestService questService;
    private QuestReloadService reloadService;
    private Config candidate;
    private MockedStatic<Scheduler> scheduler;

    @BeforeEach
    public void setUp() {
        configProvider = mock(ConfigProvider.class);
        sqlProvider = mock(SqlProvider.class);
        definitionService = mock(QuestDefinitionService.class);
        resetService = mock(QuestResetService.class);
        apiService = mock(QuestApiService.class);
        globalQuestService = mock(GlobalQuestService.class);
        questService = mock(QuestService.class);
        reloadService = new QuestReloadService(configProvider, sqlProvider, definitionService, resetService, apiService, globalQuestService, questService);
        candidate = new Config();
        when(configProvider.loadFromDisk()).thenReturn(candidate);

        SyncScheduler syncScheduler = mock(SyncScheduler.class);
        when(syncScheduler.run(any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> task = invocation.getArgument(0);
            task.accept(mock(ScheduledTask.class));
            return null;
        });
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(Scheduler::sync).thenReturn(syncScheduler);
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
    }

    @Test
    public void successfulReloadAppliesConfigThenReloadsServicesInOrder() {
        when(sqlProvider.reload(candidate.getStorage())).thenReturn(CompletableFuture.completedFuture(new SqlProvider.ReloadResult.Reconnected()));

        QuestReloadService.ReloadOutcome outcome = reloadService.reload().join();

        assertInstanceOf(QuestReloadService.ReloadOutcome.Reloaded.class, outcome);
        InOrder order = inOrder(configProvider, definitionService, resetService, apiService, globalQuestService, questService);
        order.verify(configProvider).apply(candidate);
        order.verify(definitionService).reload();
        order.verify(resetService).reload();
        order.verify(apiService).reload();
        order.verify(globalQuestService).reload();
        order.verify(questService).claimRetroactiveMilestonesForOnlinePlayers();
    }

    @Test
    public void unreadableConfigFailsWithoutTouchingStorage() {
        IllegalStateException failure = new IllegalStateException("bad yaml");
        when(configProvider.loadFromDisk()).thenThrow(failure);

        QuestReloadService.ReloadOutcome outcome = reloadService.reload().join();

        QuestReloadService.ReloadOutcome.Failed failed = assertInstanceOf(QuestReloadService.ReloadOutcome.Failed.class, outcome);
        assertSame(failure, failed.cause());
        verify(sqlProvider, never()).reload(any());
        verify(configProvider, never()).apply(any());
    }

    @Test
    public void failedStorageReconnectKeepsThePreviousConfig() {
        IllegalStateException failure = new IllegalStateException("database down");
        when(sqlProvider.reload(candidate.getStorage())).thenReturn(CompletableFuture.completedFuture(new SqlProvider.ReloadResult.Failed(failure)));

        QuestReloadService.ReloadOutcome outcome = reloadService.reload().join();

        QuestReloadService.ReloadOutcome.Failed failed = assertInstanceOf(QuestReloadService.ReloadOutcome.Failed.class, outcome);
        assertSame(failure, failed.cause());
        verify(configProvider, never()).apply(any());
        verify(definitionService, never()).reload();
    }

    @Test
    public void exceptionalStorageReloadKeepsThePreviousConfig() {
        when(sqlProvider.reload(candidate.getStorage())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("reload thread died")));

        QuestReloadService.ReloadOutcome outcome = reloadService.reload().join();

        assertInstanceOf(QuestReloadService.ReloadOutcome.Failed.class, outcome);
        verify(configProvider, never()).apply(any());
    }

    @Test
    public void failingServiceReloadReportsFailure() {
        IllegalStateException failure = new IllegalStateException("bad definition");
        when(sqlProvider.reload(candidate.getStorage())).thenReturn(CompletableFuture.completedFuture(new SqlProvider.ReloadResult.Unchanged()));
        doThrow(failure).when(definitionService).reload();

        QuestReloadService.ReloadOutcome outcome = reloadService.reload().join();

        QuestReloadService.ReloadOutcome.Failed failed = assertInstanceOf(QuestReloadService.ReloadOutcome.Failed.class, outcome);
        assertSame(failure, failed.cause());
        verify(globalQuestService, never()).reload();
    }
}
