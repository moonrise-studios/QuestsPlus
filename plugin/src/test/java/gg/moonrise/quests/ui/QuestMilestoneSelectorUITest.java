package gg.moonrise.quests.ui;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.menu.MilestoneMenu;
import gg.moonrise.quests.entity.PlayerQuestState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestMilestoneSelectorUITest {

    private ServerMock server;
    private Player player;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("SelectorTester");
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void nullSelectorBackButtonIsTreatedAsDisabled() throws ReflectiveOperationException {
        Config config = new Config();
        MilestoneMenu menu = config.getMilestoneMenu();
        int backSlot = menu.getSelectorBackButton().getSlot();
        setField(menu, "selectorBackButton", null);
        QuestMenuService menuService = mock(QuestMenuService.class);
        QuestMenuItems items = mock(QuestMenuItems.class);
        when(menuService.items()).thenReturn(items);
        when(menuService.config()).thenReturn(config);
        when(items.difficulties()).thenReturn(List.of());
        when(items.buildMenuItem(menu.getFiller())).thenReturn(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        PlayerQuestState state = new PlayerQuestState(player.getUniqueId(), "daily", List.of());

        QuestMilestoneSelectorUI ui = new QuestMilestoneSelectorUI(player, menuService, state);

        int totalSlots = Math.clamp(menu.getSelectorRows(), 1, 6) * 9;
        assertEquals(totalSlots, ui.getButtonCount());
        assertNotNull(ui.getButton(backSlot));
        assertNull(ui.getButton(backSlot).clickAction());
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
