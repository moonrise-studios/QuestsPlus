package gg.moonrise.quests.type;

import gg.moonrise.moss.spring.SpringComponent;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SpringComponent
public class BrewingStandOutputTracker {

    private static final int FIRST_OUTPUT_SLOT = 0;
    private static final int LAST_OUTPUT_SLOT = 2;

    private final Map<StandKey, Map<Integer, PendingBrewOutput>> pendingOutputs = new HashMap<>();
    private final Map<InventoryClickEvent, BrewedTake> consumedClickEvents = Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public void recordBrew(BlockEvent event, java.util.List<ItemStack> results) {
        StandKey standKey = StandKey.from(event);
        Map<Integer, PendingBrewOutput> outputs = new HashMap<>();
        for (int slot = FIRST_OUTPUT_SLOT; slot <= LAST_OUTPUT_SLOT && slot < results.size(); slot++) {
            ItemStack item = results.get(slot);
            String itemKey = itemTypeKey(item);
            if (itemKey == null || item.getAmount() <= 0) {
                continue;
            }
            outputs.put(slot, new PendingBrewOutput(itemKey, item.getAmount()));
        }

        if (outputs.isEmpty()) {
            pendingOutputs.remove(standKey);
            return;
        }
        pendingOutputs.put(standKey, outputs);
    }

    public Optional<BrewClick> brewClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.BREWING) {
            return Optional.empty();
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return Optional.empty();
        }
        if (!isOutputSlot(event.getSlot()) || !removesFromSlot(event.getAction())) {
            return Optional.empty();
        }

        StandKey standKey = StandKey.from(event.getView().getTopInventory());
        if (standKey == null) {
            return Optional.empty();
        }
        Map<Integer, PendingBrewOutput> pending = pendingOutputs.get(standKey);
        if (pending == null || !pending.containsKey(event.getSlot())) {
            return Optional.empty();
        }
        return Optional.of(new BrewClick(standKey, event.getSlot()));
    }

    public BrewedTake consume(InventoryClickEvent event, BrewClick click) {
        BrewedTake cached = consumedClickEvents.get(event);
        if (cached != null) {
            return cached;
        }

        BrewedTake consumed = consume(click);
        consumedClickEvents.put(event, consumed);
        return consumed;
    }

    private BrewedTake consume(BrewClick click) {
        Map<Integer, PendingBrewOutput> pending = pendingOutputs.get(click.standKey());
        if (pending == null) {
            return BrewedTake.none();
        }

        PendingBrewOutput output = pending.get(click.slot());
        if (output == null) {
            return BrewedTake.none();
        }

        Inventory inventory = click.standKey().inventory();
        int remaining = matchingAmount(inventory == null ? null : inventory.getItem(click.slot()), output.itemKey());
        int amount = Math.max(0, output.amount() - remaining);
        if (amount <= 0) {
            return BrewedTake.none();
        }

        if (remaining > 0) {
            pending.put(click.slot(), new PendingBrewOutput(output.itemKey(), remaining));
        } else {
            pending.remove(click.slot());
        }
        if (pending.isEmpty()) {
            pendingOutputs.remove(click.standKey());
        }

        return new BrewedTake(output.itemKey(), amount);
    }

    private boolean isOutputSlot(int slot) {
        return slot >= FIRST_OUTPUT_SLOT && slot <= LAST_OUTPUT_SLOT;
    }

    private boolean removesFromSlot(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL,
                 PICKUP_SOME,
                 PICKUP_HALF,
                 PICKUP_ONE,
                 SWAP_WITH_CURSOR,
                 DROP_ALL_SLOT,
                 DROP_ONE_SLOT,
                 MOVE_TO_OTHER_INVENTORY,
                 HOTBAR_MOVE_AND_READD,
                 HOTBAR_SWAP,
                 COLLECT_TO_CURSOR -> true;
            default -> false;
        };
    }

    private int matchingAmount(ItemStack item, String itemKey) {
        String currentKey = itemTypeKey(item);
        if (!itemKey.equals(currentKey)) {
            return 0;
        }
        return item == null ? 0 : item.getAmount();
    }

    private String itemTypeKey(ItemStack item) {
        if (item == null) {
            return null;
        }
        Material material = item.getType();
        if (!material.isItem() || material.isAir()) {
            return null;
        }
        ItemType itemType = material.asItemType();
        return itemType == null ? null : itemType.getKey().toString();
    }

    public record BrewClick(StandKey standKey, int slot) {
    }

    public record BrewedTake(String itemKey, int amount) {

        private static BrewedTake none() {
            return new BrewedTake("", 0);
        }

        public boolean present() {
            return amount > 0 && !itemKey.isBlank();
        }
    }

    public record StandKey(UUID worldId, int x, int y, int z, Inventory inventory) {

        private static StandKey from(BlockEvent event) {
            return new StandKey(
                    event.getBlock().getWorld().getUID(),
                    event.getBlock().getX(),
                    event.getBlock().getY(),
                    event.getBlock().getZ(),
                    null
            );
        }

        private static StandKey from(Inventory inventory) {
            if (!(inventory instanceof BrewerInventory brewerInventory)) {
                return null;
            }
            BrewingStand holder = brewerInventory.getHolder();
            if (holder == null) {
                return null;
            }
            return new StandKey(
                    holder.getWorld().getUID(),
                    holder.getX(),
                    holder.getY(),
                    holder.getZ(),
                    inventory
            );
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof StandKey other)) {
                return false;
            }
            return x == other.x && y == other.y && z == other.z && worldId.equals(other.worldId);
        }

        @Override
        public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }

    private record PendingBrewOutput(String itemKey, int amount) {
    }
}
