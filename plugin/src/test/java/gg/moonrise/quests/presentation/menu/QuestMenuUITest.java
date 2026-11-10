package gg.moonrise.quests.presentation.menu;

import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.application.quest.QuestMenuLayout;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestMenuUITest {

    private ServerMock server;
    private Player player;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("MenuTester");
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void dailyMenuPlacesQuestStatesUtilityButtonsAndFillersInExpectedSlots() {
        QuestMenuService menuService = mock(QuestMenuService.class);
        QuestMenuItems items = mock(QuestMenuItems.class);
        when(menuService.items()).thenReturn(items);
        Config config = new Config();
        GeneratedQuest activeQuest = quest(player.getUniqueId(), 0);
        PlayerQuestState state = new PlayerQuestState(player.getUniqueId(), "daily", List.of(activeQuest));
        when(menuService.config()).thenReturn(config);
        // Three daily slots and one premium slot visible.
        when(items.menuLayout()).thenReturn(new QuestMenuLayout(27, 15, Set.of(15, 18, 22, 26), List.of(10, 11, 12, 13, 14, 16), 3, 1));
        when(items.canAccessSlot(player, 0)).thenReturn(true);
        when(items.canAccessSlot(player, 1)).thenReturn(true);
        when(items.canAccessSlot(player, 2)).thenReturn(false);
        when(items.canAccessSlot(player, 3)).thenReturn(true);
        when(items.canShowMilestoneButton()).thenReturn(true);
        when(items.canShowStreakButton()).thenReturn(true);
        when(items.canShowQuestResetButton()).thenReturn(true);
        when(items.buildGlobalQuestItem(player, null)).thenReturn(new ItemStack(Material.NETHER_STAR));
        when(items.buildQuestItem(player, activeQuest)).thenReturn(new ItemStack(Material.BOOK));
        when(items.buildEmptyQuestSlotItem(1)).thenReturn(new ItemStack(Material.GRAY_DYE));
        when(items.buildEmptyQuestSlotItem(3)).thenReturn(new ItemStack(Material.LIGHT_GRAY_DYE));
        when(items.buildLockedPremiumQuestSlotItem(2)).thenReturn(new ItemStack(Material.BARRIER));
        when(items.buildMenuItem(config.getMenu().getMilestoneButton().getItem())).thenReturn(new ItemStack(Material.NETHER_STAR));
        when(items.buildMenuItem(config.getMenu().getStreakButton().getItem())).thenReturn(new ItemStack(Material.BLAZE_POWDER));
        when(items.buildQuestResetButtonItem(player, state)).thenReturn(new ItemStack(Material.AMETHYST_SHARD));
        when(items.buildMenuItem(config.getMenu().getFiller())).thenReturn(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

        QuestMenuUI ui = new QuestMenuUI(player, menuService, state, null);

        assertEquals(27, ui.getButtonCount());
        assertButtonItem(Material.BOOK, ui.getButton(10));
        assertButtonItem(Material.GRAY_DYE, ui.getButton(11));
        assertButtonItem(Material.BARRIER, ui.getButton(12));
        assertButtonItem(Material.LIGHT_GRAY_DYE, ui.getButton(13));
        assertButtonItem(Material.NETHER_STAR, ui.getButton(15));
        assertButtonItem(Material.AMETHYST_SHARD, ui.getButton(18));
        assertButtonItem(Material.NETHER_STAR, ui.getButton(22));
        assertButtonItem(Material.BLAZE_POWDER, ui.getButton(26));
        assertNotNull(ui.getButton(0));
        assertNull(ui.getButton(0).clickAction());
        assertNull(ui.getButton(12).clickAction());

        ui.getButton(10).processClickAction(player, null);
        ui.getButton(11).processClickAction(player, null);
        ui.getButton(15).processClickAction(player, null);
        ui.getButton(18).processClickAction(player, null);
        ui.getButton(22).processClickAction(player, null);
        ui.getButton(26).processClickAction(player, null);

        verify(menuService).openRerollDifficultyPicker(player, 0);
        verify(menuService).openDifficultyPicker(player, 1);
        verify(menuService).openGlobalRewardPreview(player);
        verify(menuService).openQuestResetPurchase(player);
        verify(menuService).openMilestoneSelector(player);
        verify(menuService).openStreaks(player);
    }

    private void assertButtonItem(Material expected, Button button) {
        assertNotNull(button);
        assertEquals(expected, button.item(player).getType());
    }

    private static GeneratedQuest quest(UUID playerId, int slotIndex) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                "daily",
                "kill-mobs",
                QuestTypes.KILL_MOB,
                "easy",
                "<green>Easy",
                "<green>Mob Hunt",
                List.of("<gray>Kill mobs."),
                Map.of("mob", "zombie"),
                slotIndex,
                10,
                0,
                false
        );
    }
}
