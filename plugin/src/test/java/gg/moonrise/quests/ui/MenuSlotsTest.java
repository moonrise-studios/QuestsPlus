package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class MenuSlotsTest {

    private final Map<Integer, Button> placed = new LinkedHashMap<>();
    private final List<Integer> placementOrder = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void addIgnoresSlotsOutsideTheMenu() {
        MenuSlots menuSlots = new MenuSlots(this::capture, 9);
        Button inside = button();

        menuSlots.add(-1, button());
        menuSlots.add(9, button());
        menuSlots.add(4, inside);

        assertEquals(List.of(4), placementOrder);
        assertSame(inside, placed.get(4));
    }

    @Test
    public void fillPlacesFillersOnlyInUnoccupiedSlots() {
        QuestMenuService menuService = mock(QuestMenuService.class);
        Config.MenuItem fillerItem = new Config().getMenu().getFiller();
        MenuSlots menuSlots = new MenuSlots(this::capture, 9);
        Button first = button();
        Button last = button();
        menuSlots.add(0, first);
        menuSlots.add(8, last);

        menuSlots.fill(menuService, fillerItem);

        assertEquals(List.of(0, 8, 1, 2, 3, 4, 5, 6, 7), placementOrder);
        assertSame(first, placed.get(0));
        assertSame(last, placed.get(8));
        for (int slot = 1; slot <= 7; slot++) {
            Button filler = placed.get(slot);
            assertNotSame(first, filler);
            assertNotSame(last, filler);
            assertNull(filler.clickAction());
        }
    }

    @Test
    public void rowsAreClampedToChestSizeAndConvertedToSlots() {
        assertEquals(1, MenuSlots.clampRows(0));
        assertEquals(3, MenuSlots.clampRows(3));
        assertEquals(6, MenuSlots.clampRows(10));
        assertEquals(27, MenuSlots.totalSlots(3));
    }

    private static Button button() {
        return Button.builder()
                .item(viewer -> new ItemStack(Material.STONE))
                .build();
    }

    private void capture(int slot, Button button) {
        placementOrder.add(slot);
        placed.put(slot, button);
    }
}
