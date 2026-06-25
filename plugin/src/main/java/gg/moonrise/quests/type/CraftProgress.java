package gg.moonrise.quests.type;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

final class CraftProgress {

    private CraftProgress() {
    }

    static int amountCrafted(CraftItemEvent event, ItemStack result, Player player) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        if (event.getAction() == InventoryAction.NOTHING || event.getAction() == InventoryAction.UNKNOWN) {
            return 0;
        }
        if (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return shiftCraftedAmount(event.getInventory(), result, player);
        }
        return result.getAmount();
    }

    private static int shiftCraftedAmount(CraftingInventory inventory, ItemStack result, Player player) {
        int possibleCrafts = possibleCrafts(inventory);
        if (possibleCrafts <= 0) {
            return 0;
        }

        int possibleItems = possibleCrafts * result.getAmount();
        int inventorySpace = inventorySpace(player, result);
        return Math.min(possibleItems, inventorySpace);
    }

    private static int possibleCrafts(CraftingInventory inventory) {
        int possible = Integer.MAX_VALUE;
        for (ItemStack ingredient : inventory.getMatrix()) {
            if (ingredient == null || ingredient.getType() == Material.AIR || ingredient.getAmount() <= 0) {
                continue;
            }
            possible = Math.min(possible, ingredient.getAmount());
        }
        return possible == Integer.MAX_VALUE ? 0 : possible;
    }

    private static int inventorySpace(Player player, ItemStack result) {
        int maxStackSize = result.getMaxStackSize();
        int space = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                space += maxStackSize;
                continue;
            }
            if (item.isSimilar(result) && item.getAmount() < maxStackSize) {
                space += maxStackSize - item.getAmount();
            }
        }
        return space;
    }
}
