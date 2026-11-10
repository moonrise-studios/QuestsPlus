package gg.moonrise.quests.type;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CraftProgressTest {

    @Test
    public void normalCraftClickCountsResultAmount() {
        CraftItemEvent event = mock(CraftItemEvent.class);
        Player player = mock(Player.class);
        ItemStack result = new ItemStack(Material.TORCH, 4);

        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.isShiftClick()).thenReturn(false);

        assertEquals(4, CraftProgress.amountCrafted(event, result, player));
    }

    @Test
    public void shiftCraftCountsCraftableOutputThatFitsInStorage() {
        CraftItemEvent event = mock(CraftItemEvent.class);
        CraftingInventory craftingInventory = mock(CraftingInventory.class);
        Player player = mock(Player.class);
        PlayerInventory playerInventory = mock(PlayerInventory.class);
        ItemStack result = new ItemStack(Material.TORCH, 4);

        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.isShiftClick()).thenReturn(true);
        when(event.getInventory()).thenReturn(craftingInventory);
        when(craftingInventory.getMatrix()).thenReturn(new ItemStack[]{
                new ItemStack(Material.COAL, 12),
                new ItemStack(Material.STICK, 10)
        });
        when(player.getInventory()).thenReturn(playerInventory);
        when(playerInventory.getStorageContents()).thenReturn(new ItemStack[]{
                new ItemStack(Material.TORCH, 60),
                null
        });

        assertEquals(40, CraftProgress.amountCrafted(event, result, player));
    }

    @Test
    public void shiftCraftIsCappedByInventorySpace() {
        CraftItemEvent event = mock(CraftItemEvent.class);
        CraftingInventory craftingInventory = mock(CraftingInventory.class);
        Player player = mock(Player.class);
        PlayerInventory playerInventory = mock(PlayerInventory.class);
        ItemStack result = new ItemStack(Material.CHEST, 1);

        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.isShiftClick()).thenReturn(true);
        when(event.getInventory()).thenReturn(craftingInventory);
        when(craftingInventory.getMatrix()).thenReturn(new ItemStack[]{
                new ItemStack(Material.OAK_PLANKS, 64),
                new ItemStack(Material.OAK_PLANKS, 64),
                new ItemStack(Material.OAK_PLANKS, 64),
                new ItemStack(Material.OAK_PLANKS, 64),
                new ItemStack(Material.OAK_PLANKS, 64),
                new ItemStack(Material.OAK_PLANKS, 64),
                new ItemStack(Material.OAK_PLANKS, 64),
                new ItemStack(Material.OAK_PLANKS, 64)
        });
        when(player.getInventory()).thenReturn(playerInventory);
        when(playerInventory.getStorageContents()).thenReturn(new ItemStack[]{
                new ItemStack(Material.CHEST, 63)
        });

        assertEquals(1, CraftProgress.amountCrafted(event, result, player));
    }
}
