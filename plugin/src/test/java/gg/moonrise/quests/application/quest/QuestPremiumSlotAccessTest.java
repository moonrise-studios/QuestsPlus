package gg.moonrise.quests.application.quest;

import gg.moonrise.engine.message.Message;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.Currencies;
import gg.moonrise.quests.config.DailyFile;
import gg.moonrise.quests.config.MilestoneMenu;
import gg.moonrise.quests.config.MilestoneMessages;
import gg.moonrise.quests.config.PremiumQuestMenu;
import gg.moonrise.quests.config.PremiumQuestsFile;
import gg.moonrise.quests.config.QuestMenu;
import gg.moonrise.quests.config.QuestMenuFile;
import gg.moonrise.quests.config.QuestMilestonesFile;
import gg.moonrise.quests.config.QuestResetMenu;
import gg.moonrise.quests.config.SharedMessagesFile;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.domain.model.QuestResetEligibility;
import gg.moonrise.quests.domain.model.QuestSelectionStatus;
import gg.moonrise.quests.domain.model.QuestResetPurchaseOutcome;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.infrastructure.persistence.QuestRepository;
import gg.moonrise.quests.application.global.GlobalQuestService;
import gg.moonrise.quests.application.indicator.QuestProgressIndicatorService;
import gg.moonrise.quests.application.reset.QuestResetPurchaseService;
import gg.moonrise.quests.application.reset.QuestResetService;
import gg.moonrise.quests.application.streak.QuestStreakService;
import gg.moonrise.quests.core.service.ConfigProvider;
import gg.moonrise.quests.presentation.menu.QuestMenuItems;
import gg.moonrise.quests.presentation.menu.QuestResetCurrencies;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestPremiumSlotAccessTest {

    @Test
    public void highestConfiguredPremiumLimitControlsVisibleSlots() throws Exception {
        QuestServices service = questServices(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2, "mvp", 3),
                Map.of()
        ));
        Player player = mock(Player.class);

        assertEquals(3, service.slots().visiblePremiumSlotLimit());
        assertEquals(6, service.slots().totalVisibleQuestSlots());
        assertEquals(0, service.slots().premiumSlotLimit(player));
        assertEquals(3, service.slots().totalQuestSlots(player));
    }

    @Test
    public void playerPermissionLimitControlsUsablePremiumSlots() throws Exception {
        QuestServices service = questServices(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2, "mvp", 3),
                Map.of()
        ));
        Player player = mock(Player.class);
        when(player.hasPermission("questsplus.premium.vipplus")).thenReturn(true);

        assertEquals(2, service.slots().premiumSlotLimit(player));
        assertTrue(service.slots().canAccessSlot(player, 3));
        assertTrue(service.slots().canAccessSlot(player, 4));
        assertFalse(service.slots().canAccessSlot(player, 5));
    }

    @Test
    public void premiumQuestsBeyondPermissionLimitRemainInaccessible() throws Exception {
        QuestServices service = questServices(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2, "mvp", 3),
                Map.of()
        ));
        Player player = mock(Player.class);
        when(player.hasPermission("questsplus.premium.vip")).thenReturn(true);

        assertTrue(service.slots().canAccessQuest(player, premiumQuest(3)));
        assertFalse(service.slots().canAccessQuest(player, premiumQuest(4)));
        assertFalse(service.slots().canAccessSlot(player, 4));
    }

    @Test
    public void selectionAndRerollRejectLockedPremiumSlots() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2, "mvp", 3),
                Map.of()
        );
        ConfigProvider configProvider = mock(ConfigProvider.class);
        QuestRepository repository = mock(QuestRepository.class);
        QuestServices service = questServices(configProvider, repository);
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(configProvider.get()).thenReturn(config);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission("questsplus.premium.vip")).thenReturn(true);
        when(repository.loadQuests(playerId, "daily")).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));

        assertEquals(QuestSelectionStatus.INVALID, service.selection().selectQuestDifficulty(player, "daily", 4, "easy").join().status());
        assertEquals(QuestSelectionStatus.INVALID, service.selection().rerollQuestDifficulty(player, "daily", 4, "easy").join().status());
    }

    @Test
    public void lockedPremiumRanksUseOneBasedDisplayedSlotNumbers() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of(
                        "4", "<green>VIP",
                        "5", "<aqua>VIP+"
                )
        );
        QuestMenuItems menuItems = questMenuItems(config);

        assertEquals("<green>VIP", menuItems.lockedPremiumRank(3));
        assertEquals("<aqua>VIP+", menuItems.lockedPremiumRank(4));
    }

    @Test
    public void questResetRequiresEveryNormalSlotComplete() throws Exception {
        QuestServices service = questServices(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        ));
        Player player = mock(Player.class);
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", List.of(
                normalQuest(0, true),
                normalQuest(1, true),
                normalQuest(2, false)
        ));

        QuestResetEligibility eligibility = service.slots().resetEligibility(player, state);

        assertEquals(2, eligibility.completed());
        assertEquals(3, eligibility.required());
        assertFalse(eligibility.eligible());
    }

    @Test
    public void questResetIncludesAccessiblePremiumSlots() throws Exception {
        QuestServices service = questServices(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        ));
        Player player = mock(Player.class);
        when(player.hasPermission("questsplus.premium.vip")).thenReturn(true);
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", List.of(
                normalQuest(0, true),
                normalQuest(1, true),
                normalQuest(2, true),
                premiumQuest(3, true),
                premiumQuest(4, false)
        ));

        QuestResetEligibility eligibility = service.slots().resetEligibility(player, state);

        assertEquals(4, eligibility.completed());
        assertEquals(4, eligibility.required());
        assertTrue(eligibility.eligible());
    }

    @Test
    public void questResetRequiresUnlockedPremiumSlotToBeComplete() throws Exception {
        QuestServices service = questServices(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        ));
        Player player = mock(Player.class);
        when(player.hasPermission("questsplus.premium.vipplus")).thenReturn(true);
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", List.of(
                normalQuest(0, true),
                normalQuest(1, true),
                normalQuest(2, true),
                premiumQuest(3, true),
                premiumQuest(4, false)
        ));

        QuestResetEligibility eligibility = service.slots().resetEligibility(player, state);

        assertEquals(4, eligibility.completed());
        assertEquals(5, eligibility.required());
        assertFalse(eligibility.eligible());
    }

    @Test
    public void questResetPurchaseLimitDefaultsToOne() throws Exception {
        QuestServices service = questServices(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        ));

        assertEquals(1, service.usage().questResetDailyLimit());
    }

    @Test
    public void questResetPurchaseLimitClampsNegativeToZero() {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of(),
                new QuestResetMenu(-1)
        );
        QuestServices service = questServices(config);

        assertEquals(0, service.usage().questResetDailyLimit());
    }

    @Test
    public void questResetStatusOutputsAreConfigurable() {
        QuestResetMenu menu = new QuestResetMenu();

        assertEquals("Ready", menu.getStatusReady());
        assertEquals("Complete all quests", menu.getStatusIncomplete());
        assertEquals("You have already used your resets for the day", menu.getStatusLimitReached());

        menu = new QuestResetMenu("Can reset", "Finish your quests", "No resets left");

        assertEquals("Can reset", menu.getStatusReady());
        assertEquals("Finish your quests", menu.getStatusIncomplete());
        assertEquals("No resets left", menu.getStatusLimitReached());
    }

    @Test
    public void exhaustedQuestResetPurchaseLimitBlocksBeforeRepositoryMutation() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        QuestRepository repository = mock(QuestRepository.class);
        QuestServices service = questServices(configProvider(config), repository);
        UUID playerId = UUID.randomUUID();
        when(repository.loadQuestResetsUsed(playerId, "daily")).thenReturn(CompletableFuture.completedFuture(1));

        service.usage().ensureQuestResetPurchaseUsageAsync(playerId, "daily").join();

        assertEquals(0, service.usage().questResetPurchasesRemaining(playerId, "daily"));
        verify(repository, never()).recordQuestResetPurchaseAndReset(playerId, "daily", 1);
    }

    @Test
    public void successfulQuestResetPurchaseRecordsUsageAndClearsGeneratedQuests() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        QuestRepository repository = mock(QuestRepository.class);
        QuestServices service = questServices(configProvider(config), repository);
        UUID playerId = UUID.randomUUID();
        when(repository.recordQuestResetPurchaseAndReset(playerId, "daily", 1)).thenReturn(CompletableFuture.completedFuture(new QuestResetPurchaseOutcome.Applied(1)));

        assertEquals(new QuestResetPurchaseOutcome.Applied(1), service.usage().recordQuestResetPurchaseAndReset(playerId, "daily").join());
        assertEquals(1, service.usage().cachedQuestResetPurchasesUsed(playerId, "daily"));
        assertEquals(0, service.usage().questResetPurchasesRemaining(playerId, "daily"));
        verify(repository).recordQuestResetPurchaseAndReset(playerId, "daily", 1);
    }

    @Test
    public void questResetPurchaseAtLimitLeavesCachedUsageUnchanged() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        QuestRepository repository = mock(QuestRepository.class);
        QuestServices service = questServices(configProvider(config), repository);
        UUID playerId = UUID.randomUUID();
        when(repository.recordQuestResetPurchaseAndReset(playerId, "daily", 1))
                .thenReturn(CompletableFuture.completedFuture(new QuestResetPurchaseOutcome.LimitReached()));

        assertEquals(new QuestResetPurchaseOutcome.LimitReached(), service.usage().recordQuestResetPurchaseAndReset(playerId, "daily").join());
        assertEquals(0, service.usage().cachedQuestResetPurchasesUsed(playerId, "daily"));
        assertEquals(1, service.usage().questResetPurchasesRemaining(playerId, "daily"));
    }

    @Test
    public void questResetPurchaseUsageIsKeyedByResetWindow() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        QuestRepository repository = mock(QuestRepository.class);
        QuestServices service = questServices(configProvider(config), repository);
        UUID playerId = UUID.randomUUID();
        when(repository.loadQuestResetsUsed(playerId, "daily-1")).thenReturn(CompletableFuture.completedFuture(1));
        when(repository.loadQuestResetsUsed(playerId, "daily-2")).thenReturn(CompletableFuture.completedFuture(0));

        service.usage().ensureQuestResetPurchaseUsageAsync(playerId, "daily-1").join();
        service.usage().ensureQuestResetPurchaseUsageAsync(playerId, "daily-2").join();

        assertEquals(0, service.usage().questResetPurchasesRemaining(playerId, "daily-1"));
        assertEquals(1, service.usage().questResetPurchasesRemaining(playerId, "daily-2"));
    }

    @Test
    public void adminResetDoesNotRecordQuestResetPurchaseUsage() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        QuestRepository repository = mock(QuestRepository.class);
        QuestServices service = questServices(configProvider(config), repository);
        UUID playerId = UUID.randomUUID();
        when(repository.resetDailyQuests(playerId, "daily")).thenReturn(CompletableFuture.completedFuture(null));

        service.state().resetDaily(playerId, "daily").join();

        verify(repository).resetDailyQuests(playerId, "daily");
        verify(repository, never()).recordQuestResetPurchaseAndReset(playerId, "daily", 1);
    }

    @Test
    public void milestoneMessagesComposeIntoRuntimeMessages() {
        MilestoneMessages milestoneMessages = new MilestoneMessages(
                Message.of("<green>Completed <milestone_display_name>"),
                Message.of("<green>Claimed <milestone_display_name>")
        );
        SharedMessagesFile sharedMessages = new SharedMessagesFile(milestoneMessages);

        Config config = Config.compose(null, new DailyFile(), null, null, sharedMessages, null, null);

        assertEquals("<green>Completed <milestone_display_name>", config.getMessages().getMilestoneCompleted().content());
        assertEquals("<green>Claimed <milestone_display_name>", config.getMessages().getMilestoneClaimed().content());
    }

    @Test
    public void questMilestonesFileOverridesSharedMilestoneMessages() {
        MilestoneMessages sharedMilestoneMessages = new MilestoneMessages(
                Message.of("<red>Legacy completed"),
                Message.of("<red>Legacy claimed")
        );
        MilestoneMessages modularMilestoneMessages = new MilestoneMessages(
                Message.of("<green>Modular completed"),
                Message.of("<green>Modular claimed")
        );
        SharedMessagesFile sharedMessages = new SharedMessagesFile(sharedMilestoneMessages);
        QuestMilestonesFile questMilestones = new QuestMilestonesFile(modularMilestoneMessages, new MilestoneMenu());

        Config config = Config.compose(null, new DailyFile(), null, null, sharedMessages, null, null, questMilestones, null, (Currencies) null);

        assertEquals("<green>Modular completed", config.getMessages().getMilestoneCompleted().content());
        assertEquals("<green>Modular claimed", config.getMessages().getMilestoneClaimed().content());
    }

    @Test
    public void questMenuFileOverridesDailyMenuFallback() {
        QuestMenu legacyMenu = new QuestMenu(new QuestResetMenu("Legacy", "Legacy incomplete", "Legacy limit"));
        QuestMenu modularMenu = new QuestMenu(new QuestResetMenu("Modular", "Modular incomplete", "Modular limit"));
        DailyFile daily = new DailyFile(3, legacyMenu);
        QuestMenuFile questMenu = new QuestMenuFile(modularMenu);

        Config config = Config.compose(null, daily, null, null, null, null, null, questMenu, null, null, null, (Currencies) null);

        assertEquals("Modular", config.getMenu().getResetMenu().getStatusReady());
    }

    private static QuestServices questServices(Config config) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return questServices(configProvider, mock(QuestRepository.class));
    }

    private static QuestServices questServices(ConfigProvider configProvider, QuestRepository questRepository) {
        return QuestServices.create(
                configProvider,
                mock(QuestDefinitionService.class),
                questRepository,
                mock(QuestStreakService.class),
                mock(GlobalQuestService.class),
                mock(QuestProgressIndicatorService.class)
        );
    }

    private static ConfigProvider configProvider(Config config) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return configProvider;
    }

    private static QuestMenuItems questMenuItems(Config config) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return new QuestMenuItems(
                configProvider,
                mock(QuestDefinitionService.class),
                new QuestSlotService(configProvider),
                mock(QuestUsageService.class),
                mock(QuestTextService.class),
                mock(QuestResetService.class),
                mock(QuestStreakService.class),
                mock(GlobalQuestService.class),
                new QuestResetCurrencies(configProvider, mock(QuestResetPurchaseService.class))
        );
    }

    private static Config config(int questCount, Map<String, Integer> premiumLimits, Map<String, String> lockedRanks) {
        return config(questCount, premiumLimits, lockedRanks, null);
    }

    private static Config config(int questCount, Map<String, Integer> premiumLimits, Map<String, String> lockedRanks, QuestResetMenu resetMenu) {
        DailyFile daily = resetMenu == null
                ? new DailyFile(questCount)
                : new DailyFile(questCount, new QuestMenu(resetMenu));
        PremiumQuestMenu menu = new PremiumQuestMenu(lockedRanks);
        PremiumQuestsFile premium = new PremiumQuestsFile(premiumLimits, menu);
        return Config.compose(null, daily, null, null, null, null, premium);
    }

    private static Map<String, Integer> premiumLimits(String firstKey, int firstValue, String secondKey, int secondValue) {
        return premiumLimits(firstKey, firstValue, secondKey, secondValue, null, 0);
    }

    private static Map<String, Integer> premiumLimits(String firstKey, int firstValue, String secondKey, int secondValue, String thirdKey, int thirdValue) {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put(firstKey, firstValue);
        limits.put(secondKey, secondValue);
        if (thirdKey != null) {
            limits.put(thirdKey, thirdValue);
        }
        return limits;
    }

    private static GeneratedQuest normalQuest(int slotIndex, boolean completed) {
        return quest(slotIndex, false, completed);
    }

    private static GeneratedQuest premiumQuest(int slotIndex) {
        return premiumQuest(slotIndex, false);
    }

    private static GeneratedQuest premiumQuest(int slotIndex, boolean completed) {
        return quest(slotIndex, true, completed);
    }

    private static GeneratedQuest quest(int slotIndex, boolean premium, boolean completed) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "daily",
                (premium ? "premium-" : "normal-") + slotIndex,
                QuestType.of("KILL_MOB"),
                "easy",
                "Easy",
                "Quest",
                List.of(),
                Map.of(),
                slotIndex,
                premium,
                10,
                completed ? 10 : 0,
                completed
        );
    }
}
