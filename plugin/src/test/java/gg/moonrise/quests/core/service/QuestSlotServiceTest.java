package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.DailyFile;
import gg.moonrise.quests.config.PremiumQuestMenu;
import gg.moonrise.quests.config.PremiumQuestsFile;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestSlotServiceTest {

    private ConfigProvider configProvider;
    private QuestSlotService slotService;
    private Player player;

    @BeforeEach
    public void setUp() {
        configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(Config.compose(null, new DailyFile(3), null, null, null, null, null));
        slotService = new QuestSlotService(configProvider);
        player = mock(Player.class);
    }

    @Test
    public void slotsOutsideTheVisibleRangeAreNeverAccessible() {
        when(player.hasPermission("questsplus.premium.mvp")).thenReturn(true);

        assertFalse(slotService.canAccessSlot(player, -1));
        assertFalse(slotService.canAccessSlot(player, slotService.totalVisibleQuestSlots()));
        assertTrue(slotService.canAccessSlot(player, slotService.totalVisibleQuestSlots() - 1));
    }

    @Test
    public void dailySlotsAndNonPremiumQuestsNeedNoPermission() {
        assertTrue(slotService.canAccessSlot(player, 0));
        assertTrue(slotService.canAccessSlot(player, 2));
        assertFalse(slotService.canAccessSlot(player, 3));
        // A quest stored before premium slots were configured keeps working wherever it sits.
        assertTrue(slotService.canAccessQuest(player, quest(5, false)));
        assertFalse(slotService.canAccessQuest(player, quest(3, true)));
    }

    @Test
    public void accessibleQuestsDropsPremiumQuestsAbovePermissionLimit() {
        when(player.hasPermission("questsplus.premium.vip")).thenReturn(true);
        GeneratedQuest daily = quest(0, false);
        GeneratedQuest unlocked = quest(3, true);
        GeneratedQuest locked = quest(4, true);
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", List.of(daily, unlocked, locked));

        assertEquals(List.of(daily, unlocked), slotService.accessibleQuests(player, state));
    }

    @Test
    public void premiumLimitAboveFreeMenuCapacityIsClampedToIt() {
        when(configProvider.get()).thenReturn(Config.compose(null, new DailyFile(3), null, null, null, null,
                new PremiumQuestsFile(Map.of("whale", 500), new PremiumQuestMenu(Map.of()))));
        when(player.hasPermission("questsplus.premium.whale")).thenReturn(true);
        QuestMenuLayout layout = slotService.menuLayout();

        assertEquals(layout.capacity() - 3, slotService.premiumSlotLimit(player));
        assertEquals(layout.capacity(), slotService.totalQuestSlots(player));
    }

    @Test
    public void rerollLimitIsTheHighestLimitAmongThePlayersPermissions() {
        assertEquals(0, slotService.rerollLimit(player));

        when(player.hasPermission("questsplus.reroll.vip")).thenReturn(true);
        when(player.hasPermission("questsplus.reroll.vipplus")).thenReturn(true);

        assertEquals(2, slotService.rerollLimit(player));
    }

    private static GeneratedQuest quest(int slotIndex, boolean premium) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "daily",
                "quest-" + slotIndex,
                QuestTypes.KILL_MOB,
                "easy",
                "Easy",
                "Quest",
                List.of(),
                Map.of(),
                slotIndex,
                premium,
                10,
                0,
                false
        );
    }
}
