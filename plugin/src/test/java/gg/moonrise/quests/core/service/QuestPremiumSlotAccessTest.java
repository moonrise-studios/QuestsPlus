package gg.moonrise.quests.core.service;

import gg.moonrise.engine.message.Message;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.model.QuestSelectionStatus;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
    void highestConfiguredPremiumLimitControlsVisibleSlots() throws Exception {
        QuestService service = questService(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2, "mvp", 3),
                Map.of()
        ));
        Player player = mock(Player.class);

        assertEquals(3, service.visiblePremiumSlotLimit());
        assertEquals(6, service.totalVisibleQuestSlots());
        assertEquals(0, service.premiumSlotLimit(player));
        assertEquals(3, service.totalQuestSlots(player));
    }

    @Test
    void playerPermissionLimitControlsUsablePremiumSlots() throws Exception {
        QuestService service = questService(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2, "mvp", 3),
                Map.of()
        ));
        Player player = mock(Player.class);
        when(player.hasPermission("questsplus.premium.vipplus")).thenReturn(true);

        assertEquals(2, service.premiumSlotLimit(player));
        assertTrue(service.canAccessSlot(player, 3));
        assertTrue(service.canAccessSlot(player, 4));
        assertFalse(service.canAccessSlot(player, 5));
    }

    @Test
    void premiumQuestsBeyondPermissionLimitRemainInaccessible() throws Exception {
        QuestService service = questService(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2, "mvp", 3),
                Map.of()
        ));
        Player player = mock(Player.class);
        when(player.hasPermission("questsplus.premium.vip")).thenReturn(true);

        assertTrue(service.canAccessQuest(player, premiumQuest(3)));
        assertFalse(service.canAccessQuest(player, premiumQuest(4)));
        assertFalse(service.canAccessSlot(player, 4));
    }

    @Test
    void selectionAndRerollRejectLockedPremiumSlots() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2, "mvp", 3),
                Map.of()
        );
        ConfigProvider configProvider = mock(ConfigProvider.class);
        QuestRepository repository = mock(QuestRepository.class);
        QuestService service = questService(configProvider, repository);
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(configProvider.get()).thenReturn(config);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission("questsplus.premium.vip")).thenReturn(true);
        when(repository.loadQuests(playerId, "daily")).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));

        assertEquals(QuestSelectionStatus.INVALID, service.selectQuestDifficulty(player, "daily", 4, "easy").join().status());
        assertEquals(QuestSelectionStatus.INVALID, service.rerollQuestDifficulty(player, "daily", 4, "easy").join().status());
    }

    @Test
    void lockedPremiumRanksUseOneBasedDisplayedSlotNumbers() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of(
                        "4", "<green>VIP",
                        "5", "<aqua>VIP+"
                )
        );
        QuestMenuService menuService = questMenuService(config);

        assertEquals("<green>VIP", menuService.lockedPremiumRank(3));
        assertEquals("<aqua>VIP+", menuService.lockedPremiumRank(4));
    }

    @Test
    void questResetRequiresEveryNormalSlotComplete() throws Exception {
        QuestService service = questService(config(
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

        QuestResetEligibility eligibility = service.resetEligibility(player, state);

        assertEquals(2, eligibility.completed());
        assertEquals(3, eligibility.required());
        assertFalse(eligibility.eligible());
    }

    @Test
    void questResetIncludesAccessiblePremiumSlots() throws Exception {
        QuestService service = questService(config(
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

        QuestResetEligibility eligibility = service.resetEligibility(player, state);

        assertEquals(4, eligibility.completed());
        assertEquals(4, eligibility.required());
        assertTrue(eligibility.eligible());
    }

    @Test
    void questResetRequiresUnlockedPremiumSlotToBeComplete() throws Exception {
        QuestService service = questService(config(
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

        QuestResetEligibility eligibility = service.resetEligibility(player, state);

        assertEquals(4, eligibility.completed());
        assertEquals(5, eligibility.required());
        assertFalse(eligibility.eligible());
    }

    @Test
    void questResetPurchaseLimitDefaultsToOne() throws Exception {
        QuestService service = questService(config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        ));

        assertEquals(1, service.questResetDailyLimit());
    }

    @Test
    void questResetPurchaseLimitClampsNegativeToZero() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        set(config.getMenu().getResetMenu(), "dailyLimit", -1);
        QuestService service = questService(config);

        assertEquals(0, service.questResetDailyLimit());
    }

    @Test
    void questResetStatusOutputsAreConfigurable() throws Exception {
        Config.QuestResetMenu menu = new Config.QuestResetMenu();

        assertEquals("Ready", menu.getStatusReady());
        assertEquals("Complete all quests", menu.getStatusIncomplete());
        assertEquals("You have already used your resets for the day", menu.getStatusLimitReached());

        set(menu, "statusReady", "Can reset");
        set(menu, "statusIncomplete", "Finish your quests");
        set(menu, "statusLimitReached", "No resets left");

        assertEquals("Can reset", menu.getStatusReady());
        assertEquals("Finish your quests", menu.getStatusIncomplete());
        assertEquals("No resets left", menu.getStatusLimitReached());
    }

    @Test
    void exhaustedQuestResetPurchaseLimitBlocksBeforeRepositoryMutation() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        QuestRepository repository = mock(QuestRepository.class);
        QuestService service = questService(configProvider(config), repository);
        UUID playerId = UUID.randomUUID();
        when(repository.loadQuestResetsUsed(playerId, "daily")).thenReturn(CompletableFuture.completedFuture(1));

        service.ensureQuestResetPurchaseUsageAsync(playerId, "daily").join();

        assertEquals(0, service.questResetPurchasesRemaining(playerId, "daily"));
        verify(repository, never()).recordQuestResetPurchaseAndReset(playerId, "daily", 1);
    }

    @Test
    void successfulQuestResetPurchaseRecordsUsageAndClearsGeneratedQuests() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        QuestRepository repository = mock(QuestRepository.class);
        QuestService service = questService(configProvider(config), repository);
        UUID playerId = UUID.randomUUID();
        when(repository.recordQuestResetPurchaseAndReset(playerId, "daily", 1)).thenReturn(CompletableFuture.completedFuture(1));

        assertTrue(service.recordQuestResetPurchaseAndReset(playerId, "daily").join());
        assertEquals(1, service.cachedQuestResetPurchasesUsed(playerId, "daily"));
        assertEquals(0, service.questResetPurchasesRemaining(playerId, "daily"));
        verify(repository).recordQuestResetPurchaseAndReset(playerId, "daily", 1);
    }

    @Test
    void questResetPurchaseUsageIsKeyedByResetWindow() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        QuestRepository repository = mock(QuestRepository.class);
        QuestService service = questService(configProvider(config), repository);
        UUID playerId = UUID.randomUUID();
        when(repository.loadQuestResetsUsed(playerId, "daily-1")).thenReturn(CompletableFuture.completedFuture(1));
        when(repository.loadQuestResetsUsed(playerId, "daily-2")).thenReturn(CompletableFuture.completedFuture(0));

        service.ensureQuestResetPurchaseUsageAsync(playerId, "daily-1").join();
        service.ensureQuestResetPurchaseUsageAsync(playerId, "daily-2").join();

        assertEquals(0, service.questResetPurchasesRemaining(playerId, "daily-1"));
        assertEquals(1, service.questResetPurchasesRemaining(playerId, "daily-2"));
    }

    @Test
    void adminResetDoesNotRecordQuestResetPurchaseUsage() throws Exception {
        Config config = config(
                3,
                premiumLimits("vip", 1, "vipplus", 2),
                Map.of()
        );
        QuestRepository repository = mock(QuestRepository.class);
        QuestService service = questService(configProvider(config), repository);
        UUID playerId = UUID.randomUUID();
        when(repository.resetDailyQuests(playerId, "daily")).thenReturn(CompletableFuture.completedFuture(null));

        service.resetDaily(playerId, "daily").join();

        verify(repository).resetDailyQuests(playerId, "daily");
        verify(repository, never()).recordQuestResetPurchaseAndReset(playerId, "daily", 1);
    }

    @Test
    void milestoneMessagesComposeIntoRuntimeMessages() throws Exception {
        Config.MilestoneMessages milestoneMessages = new Config.MilestoneMessages();
        set(milestoneMessages, "milestoneCompleted", Message.of("<green>Completed <milestone_display_name>"));
        set(milestoneMessages, "milestoneClaimed", Message.of("<green>Claimed <milestone_display_name>"));

        Config.SharedMessagesFile sharedMessages = new Config.SharedMessagesFile();
        set(sharedMessages, "milestoneMessages", milestoneMessages);

        Config config = Config.compose(null, new Config.DailyFile(), null, null, sharedMessages, null, null);

        assertEquals("<green>Completed <milestone_display_name>", config.getMessages().getMilestoneCompleted().content());
        assertEquals("<green>Claimed <milestone_display_name>", config.getMessages().getMilestoneClaimed().content());
    }

    private static QuestService questService(Config config) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return questService(configProvider, mock(QuestRepository.class));
    }

    private static QuestService questService(ConfigProvider configProvider, QuestRepository questRepository) {
        return new QuestService(
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

    private static QuestMenuService questMenuService(Config config) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return new QuestMenuService(
                configProvider,
                mock(QuestDefinitionService.class),
                questService(config),
                mock(QuestResetService.class),
                mock(QuestStreakService.class),
                mock(GlobalQuestService.class),
                mock(QuestResetPurchaseService.class)
        );
    }

    private static Config config(int questCount, Map<String, Integer> premiumLimits, Map<String, String> lockedRanks) throws Exception {
        Config.DailyFile daily = new Config.DailyFile();
        set(daily, "questCount", questCount);

        Config.PremiumQuestMenu menu = new Config.PremiumQuestMenu();
        set(menu, "lockedRanks", lockedRanks);

        Config.PremiumQuestsFile premium = new Config.PremiumQuestsFile();
        set(premium, "permissionLimits", premiumLimits);
        set(premium, "menu", menu);

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

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
