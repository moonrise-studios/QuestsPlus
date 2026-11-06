package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.EntityScheduler;
import gg.moonrise.engine.paper.scheduler.impl.SyncScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.sdk.currency.QuestCurrencies;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestMenuServiceResetPurchaseTest {

    private static final String RESET_KEY = "daily";
    private static final double COST = 25.0D;

    private final Deque<SchedulerOutcome> entityOutcomes = new ArrayDeque<>();
    private MockedStatic<Scheduler> scheduler;
    private SyncScheduler syncScheduler;
    private Player player;
    private QuestCurrency currency;
    private QuestService questService;
    private QuestResetPurchaseService resetPurchaseService;
    private QuestMenuService menuService;

    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
        UUID playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.run(any(), any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> action = invocation.getArgument(0);
            Runnable retired = invocation.getArgument(1);
            SchedulerOutcome outcome = entityOutcomes.isEmpty() ? SchedulerOutcome.RUN : entityOutcomes.poll();
            return switch (outcome) {
                case RUN -> {
                    action.accept(null);
                    yield mock(ScheduledTask.class);
                }
                case RETIRED_BEFORE_RUN -> {
                    retired.run();
                    yield mock(ScheduledTask.class);
                }
                case ALREADY_RETIRED -> null;
            };
        });
        syncScheduler = mock(SyncScheduler.class);
        when(syncScheduler.run(any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> action = invocation.getArgument(0);
            action.accept(null);
            return mock(ScheduledTask.class);
        });
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(() -> Scheduler.entity(player)).thenReturn(entityScheduler);
        scheduler.when(Scheduler::sync).thenReturn(syncScheduler);

        currency = mock(QuestCurrency.class);
        when(currency.key()).thenReturn(QuestCurrencies.VAULT);
        when(currency.isAvailable()).thenReturn(true);
        when(currency.questResetCost()).thenReturn(COST);
        when(currency.button()).thenReturn(QuestCurrencyButton.of(11, "GOLD_INGOT", "<gold>Money", List.of()));
        when(currency.displayName()).thenReturn("Money");
        when(currency.displayAmount(any())).thenReturn("25");
        when(currency.charge(any(), anyDouble())).thenReturn(true);
        when(currency.refund(any(), anyDouble())).thenReturn(true);
        resetPurchaseService = new QuestResetPurchaseService();
        resetPurchaseService.registerBuiltInCurrency(currency);

        PlayerQuestState state = new PlayerQuestState(playerId, RESET_KEY, List.of());
        questService = mock(QuestService.class);
        when(questService.ensurePlayerStateAsync(player, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(state));
        when(questService.refreshQuestResetPurchaseUsageAsync(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(0));
        when(questService.resetEligibility(player, state)).thenReturn(new QuestResetEligibility(3, 3));
        when(questService.questResetPurchasesRemaining(playerId, RESET_KEY)).thenReturn(1);
        when(questService.questResetDailyLimit()).thenReturn(1);

        QuestResetService resetService = mock(QuestResetService.class);
        when(resetService.currentResetKey()).thenReturn(RESET_KEY);
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        menuService = new QuestMenuService(
                configProvider,
                mock(QuestDefinitionService.class),
                questService,
                resetService,
                mock(QuestStreakService.class),
                mock(GlobalQuestService.class),
                resetPurchaseService
        );
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
        MockBukkit.unmock();
    }

    @Test
    public void notAppliedResetRefundsWhenPlayerLoggedOutBeforeCompletion() {
        when(questService.recordQuestResetPurchaseAndReset(player.getUniqueId(), RESET_KEY)).thenReturn(CompletableFuture.completedFuture(false));
        entityOutcomes.add(SchedulerOutcome.RUN);
        entityOutcomes.add(SchedulerOutcome.ALREADY_RETIRED);

        menuService.applyQuestResetPurchase(player, QuestCurrencies.VAULT);

        verify(currency).charge(player, COST);
        verify(currency, times(1)).refund(player, COST);
        verify(syncScheduler).run(any());
        assertFalse(resetPurchaseService.isProcessing(player));
    }

    @Test
    public void databaseFailureRefundsThroughRetiredCallback() {
        when(questService.recordQuestResetPurchaseAndReset(player.getUniqueId(), RESET_KEY))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));
        entityOutcomes.add(SchedulerOutcome.RUN);
        entityOutcomes.add(SchedulerOutcome.RETIRED_BEFORE_RUN);

        menuService.applyQuestResetPurchase(player, QuestCurrencies.VAULT);

        verify(currency).charge(player, COST);
        verify(currency, times(1)).refund(player, COST);
        assertFalse(resetPurchaseService.isProcessing(player));
    }

    @Test
    public void databaseFailureRefundsOnceWhilePlayerIsOnline() {
        when(questService.recordQuestResetPurchaseAndReset(player.getUniqueId(), RESET_KEY))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));

        menuService.applyQuestResetPurchase(player, QuestCurrencies.VAULT);

        verify(currency).charge(player, COST);
        verify(currency, times(1)).refund(player, COST);
        assertFalse(resetPurchaseService.isProcessing(player));
    }

    @Test
    public void synchronousDatabaseExceptionStillRefunds() {
        when(questService.recordQuestResetPurchaseAndReset(player.getUniqueId(), RESET_KEY)).thenThrow(new IllegalStateException("pool closed"));
        entityOutcomes.add(SchedulerOutcome.RUN);
        entityOutcomes.add(SchedulerOutcome.ALREADY_RETIRED);

        menuService.applyQuestResetPurchase(player, QuestCurrencies.VAULT);

        verify(currency, times(1)).refund(player, COST);
        assertFalse(resetPurchaseService.isProcessing(player));
    }

    @Test
    public void appliedResetDoesNotRefundWhenPlayerLoggedOut() {
        when(questService.recordQuestResetPurchaseAndReset(player.getUniqueId(), RESET_KEY)).thenReturn(CompletableFuture.completedFuture(true));
        entityOutcomes.add(SchedulerOutcome.RUN);
        entityOutcomes.add(SchedulerOutcome.ALREADY_RETIRED);

        menuService.applyQuestResetPurchase(player, QuestCurrencies.VAULT);

        verify(currency).charge(player, COST);
        verify(currency, never()).refund(any(), anyDouble());
        assertFalse(resetPurchaseService.isProcessing(player));
    }

    @Test
    public void playerLoggingOutBeforeChargeReleasesLockWithoutCharging() {
        entityOutcomes.add(SchedulerOutcome.ALREADY_RETIRED);

        menuService.applyQuestResetPurchase(player, QuestCurrencies.VAULT);

        verify(currency, never()).charge(any(), anyDouble());
        verify(currency, never()).refund(any(), anyDouble());
        assertFalse(resetPurchaseService.isProcessing(player));
        assertTrue(resetPurchaseService.begin(player));
    }

    private enum SchedulerOutcome {
        RUN,
        RETIRED_BEFORE_RUN,
        ALREADY_RETIRED
    }
}
