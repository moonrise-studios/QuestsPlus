package gg.moonrise.quests.type;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrewingStandOutputTrackerTest {

    private static final UUID WORLD_ID = UUID.randomUUID();

    private final ItemStack[] slots = new ItemStack[4];

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
        when(inventory.getItem(anyInt())).thenAnswer(invocation -> slots[invocation.<Integer>getArgument(0)]);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void brewEventRecordsOutputsForLaterTake() {
        brew(potion(PotionType.SWIFTNESS), null, new ItemStack(Material.SPLASH_POTION));

        BrewingStandOutputTracker.BrewedTake take = take(0);

        assertTrue(take.present());
        assertEquals("minecraft:potion", take.itemKey());
        assertEquals(1, take.amount());
        assertTrue(tracker.brewClick(click(2, InventoryAction.PICKUP_ALL)).isPresent());
        assertFalse(tracker.brewClick(click(1, InventoryAction.PICKUP_ALL)).isPresent());
    }

    @Test
    public void unbrewedStandHasNoPendingOutputs() {
        slots[0] = potion(PotionType.SWIFTNESS);

        assertFalse(tracker.brewClick(click(0, InventoryAction.PICKUP_ALL)).isPresent());
    }

    @Test
    public void takenOutputIsOnlyCreditedOnce() {
        brew(potion(PotionType.SWIFTNESS), null, null);

        assertTrue(take(0).present());
        slots[0] = potion(PotionType.SWIFTNESS);
        assertFalse(tracker.brewClick(click(0, InventoryAction.PICKUP_ALL)).isPresent());
    }

    @Test
    public void slotHoldingADifferentItemIsNotCredited() {
        brew(potion(PotionType.SWIFTNESS), null, null);
        slots[0] = potion(PotionType.POISON);

        assertFalse(tracker.brewClick(click(0, InventoryAction.PICKUP_ALL)).isPresent());
        slots[0] = potion(PotionType.SWIFTNESS);
        assertFalse(tracker.brewClick(click(0, InventoryAction.PICKUP_ALL)).isPresent());
    }

    @Test
    public void breakingTheStandDropsPendingOutputs() {
        brew(potion(PotionType.SWIFTNESS), null, null);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        Block block = standBlock();
        when(event.getBlock()).thenReturn(block);

        tracker.onBlockBreak(event);

        assertFalse(tracker.brewClick(click(0, InventoryAction.PICKUP_ALL)).isPresent());
    }

    @Test
    public void explodingTheStandDropsPendingOutputs() {
        brew(potion(PotionType.SWIFTNESS), null, null);
        List<Block> blocks = List.of(standBlock());
        BlockExplodeEvent blockExplode = mock(BlockExplodeEvent.class);
        when(blockExplode.blockList()).thenReturn(blocks);

        tracker.onBlockExplode(blockExplode);

        assertFalse(tracker.brewClick(click(0, InventoryAction.PICKUP_ALL)).isPresent());

        brew(potion(PotionType.SWIFTNESS), null, null);
        EntityExplodeEvent entityExplode = mock(EntityExplodeEvent.class);
        when(entityExplode.blockList()).thenReturn(blocks);

        tracker.onEntityExplode(entityExplode);

        assertFalse(tracker.brewClick(click(0, InventoryAction.PICKUP_ALL)).isPresent());
    }

    @Test
    public void hopperExtractionRemovesPendingOutput() {
        brew(potion(PotionType.SWIFTNESS), potion(PotionType.SWIFTNESS), null);
        InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
        ItemStack moved = potion(PotionType.SWIFTNESS);
        when(event.getSource()).thenReturn(inventory);
        when(event.getItem()).thenReturn(moved);

        tracker.onInventoryMoveItem(event);
        slots[0] = potion(PotionType.SWIFTNESS);

        assertFalse(tracker.brewClick(click(0, InventoryAction.PICKUP_ALL)).isPresent());
        assertTrue(take(1).present());
    }

    private void brew(ItemStack... results) {
        List<ItemStack> resultList = new ArrayList<>();
        for (int slot = 0; slot < results.length; slot++) {
            slots[slot] = results[slot] == null ? null : results[slot].clone();
            resultList.add(results[slot]);
        }

        BrewEvent event = mock(BrewEvent.class);
        Block block = standBlock();
        when(event.getBlock()).thenReturn(block);
        when(event.getResults()).thenReturn(resultList);
        tracker.onBrewComplete(event);
    }

    private BrewingStandOutputTracker.BrewedTake take(int slot) {
        InventoryClickEvent click = click(slot, InventoryAction.PICKUP_ALL);
        BrewingStandOutputTracker.BrewClick brewClick = tracker.brewClick(click).orElseThrow();
        slots[slot] = null;
        return tracker.consume(click, brewClick);
    }

    private Block standBlock() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.BREWING_STAND);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(10);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(-5);
        return block;
    }

    private InventoryClickEvent click(int slot, InventoryAction action) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getSlot()).thenReturn(slot);
        when(event.getAction()).thenReturn(action);
        when(event.getCurrentItem()).thenReturn(slots[slot]);
        return event;
    }

    private static ItemStack potion(PotionType type) {
        ItemStack potion = new ItemStack(Material.POTION);
        potion.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return potion;
    }
}
