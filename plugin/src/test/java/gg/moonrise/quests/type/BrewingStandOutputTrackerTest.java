package gg.moonrise.quests.type;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrewingStandOutputTrackerTest {

    private static final UUID WORLD_ID = UUID.randomUUID();

    private BrewingStandOutputTracker tracker;
    private World world;
    private BrewerInventory inventory;

    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
        tracker = new BrewingStandOutputTracker();
        world = mock(World.class);
        when(world.getUID()).thenReturn(WORLD_ID);

        BrewingStand stand = mock(BrewingStand.class);
        when(stand.getWorld()).thenReturn(world);
        when(stand.getX()).thenReturn(10);
        when(stand.getY()).thenReturn(64);
        when(stand.getZ()).thenReturn(-5);

        inventory = mock(BrewerInventory.class);
        when(inventory.getType()).thenReturn(InventoryType.BREWING);
        when(inventory.getHolder()).thenReturn(stand);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void brewEventRecordsOutputsForLaterTake() {
        tracker.onBrewComplete(brewEvent(new ItemStack(Material.POTION), null, new ItemStack(Material.SPLASH_POTION)));

        InventoryClickEvent click = click(0, InventoryAction.PICKUP_ALL);
        BrewingStandOutputTracker.BrewClick brewClick = tracker.brewClick(click).orElseThrow();
        BrewingStandOutputTracker.BrewedTake take = tracker.consume(click, brewClick);

        assertTrue(take.present());
        assertEquals("minecraft:potion", take.itemKey());
        assertEquals(1, take.amount());
        assertTrue(tracker.brewClick(click(2, InventoryAction.PICKUP_ALL)).isPresent());
        assertFalse(tracker.brewClick(click(1, InventoryAction.PICKUP_ALL)).isPresent());
    }

    @Test
    public void unbrewedStandHasNoPendingOutputs() {
        assertFalse(tracker.brewClick(click(0, InventoryAction.PICKUP_ALL)).isPresent());
    }

    private BrewEvent brewEvent(ItemStack... results) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(10);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(-5);

        BrewEvent event = mock(BrewEvent.class);
        when(event.getBlock()).thenReturn(block);
        List<ItemStack> resultList = Arrays.asList(results);
        when(event.getResults()).thenReturn(resultList);
        return event;
    }

    private InventoryClickEvent click(int slot, InventoryAction action) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getSlot()).thenReturn(slot);
        when(event.getAction()).thenReturn(action);
        return event;
    }
}
