package gg.moonrise.quests.presentation.goal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.moonrise.moss.spring.SpringComponent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Remembers what each brewing stand just brewed so brew quests only count potions taken out
 * of a stand after a completed brew, not potions placed into it by hand.
 */
@SpringComponent
public class BrewingStandOutputTracker implements Listener {

    private static final int FIRST_OUTPUT_SLOT = 0;
    private static final int LAST_OUTPUT_SLOT = 2;
    private static final Duration PENDING_OUTPUT_EXPIRY = Duration.ofHours(1);

    private final Cache<StandKey, Map<Integer, PendingBrewOutput>> pendingOutputs = Caffeine.newBuilder()
            .expireAfterWrite(PENDING_OUTPUT_EXPIRY)
            .maximumSize(10_000)
            .build();
    private final Map<InventoryClickEvent, BrewedTake> consumedClickEvents = Collections.synchronizedMap(new WeakHashMap<>());

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewComplete(BrewEvent event) {
        recordBrew(event.getBlock(), event.getResults());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        forgetStand(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::forgetStand);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::forgetStand);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        if (source.getType() != InventoryType.BREWING || pendingOutputs.estimatedSize() == 0) {
            return;
        }
        Optional<StandKey> stand = StandKey.from(source);
        if (stand.isEmpty() || pendingOutputs.getIfPresent(stand.get()) == null) {
            return;
        }
        StandKey standKey = stand.get();

        // Hoppers take from the first output slot holding the moved item, so that slot loses one item.
        ItemStack moved = event.getItem();
        for (int slot = FIRST_OUTPUT_SLOT; slot <= LAST_OUTPUT_SLOT; slot++) {
            ItemStack current = source.getItem(slot);
            if (current != null && current.isSimilar(moved)) {
                int movedAmount = Math.min(moved.getAmount(), current.getAmount());
                reducePending(standKey, slot, current.getAmount() - movedAmount);
                return;
            }
        }
    }

    public Optional<BrewClick> brewClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getType() != InventoryType.BREWING) {
            return Optional.empty();
        }
        if (event.getClickedInventory() != topInventory) {
            return Optional.empty();
        }
        if (!isOutputSlot(event.getSlot()) || !removesFromSlot(event.getAction())) {
            return Optional.empty();
        }

        Optional<StandKey> stand = StandKey.from(topInventory);
        if (stand.isEmpty()) {
            return Optional.empty();
        }
        StandKey standKey = stand.get();

        // Drop credit the slot can no longer back, e.g. after a hopper emptied it and something else was put in.
        ItemStack current = event.getCurrentItem();
        reducePending(standKey, event.getSlot(), current == null ? 0 : current.getAmount(), current);
        Map<Integer, PendingBrewOutput> pending = pendingOutputs.getIfPresent(standKey);
        if (pending == null || !pending.containsKey(event.getSlot())) {
            return Optional.empty();
        }
        return Optional.of(new BrewClick(standKey, event.getSlot(), topInventory));
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

    private void recordBrew(Block block, List<ItemStack> results) {
        StandKey standKey = StandKey.from(block);
        Map<Integer, PendingBrewOutput> outputs = new HashMap<>();
        for (int slot = FIRST_OUTPUT_SLOT; slot <= LAST_OUTPUT_SLOT && slot < results.size(); slot++) {
            ItemStack item = results.get(slot);
            Optional<String> itemKey = itemTypeKey(item);
            if (itemKey.isEmpty() || item.getAmount() <= 0) {
                continue;
            }
            outputs.put(slot, new PendingBrewOutput(itemKey.get(), item.clone(), item.getAmount()));
        }

        if (outputs.isEmpty()) {
            pendingOutputs.invalidate(standKey);
            return;
        }
        pendingOutputs.put(standKey, Map.copyOf(outputs));
    }

    private void forgetStand(Block block) {
        if (block.getType() == Material.BREWING_STAND) {
            pendingOutputs.invalidate(StandKey.from(block));
        }
    }

    private BrewedTake consume(BrewClick click) {
        Map<Integer, PendingBrewOutput> pending = pendingOutputs.getIfPresent(click.standKey());
        PendingBrewOutput output = pending == null ? null : pending.get(click.slot());
        if (output == null) {
            return BrewedTake.none();
        }

        int remaining = matchingAmount(click.inventory().getItem(click.slot()), output);
        int amount = Math.max(0, output.amount() - remaining);
        if (amount <= 0) {
            return BrewedTake.none();
        }

        reducePending(click.standKey(), click.slot(), remaining);
        return new BrewedTake(output.itemKey(), amount);
    }

    private void reducePending(StandKey standKey, int slot, int remainingAmount) {
        reducePending(standKey, slot, remainingAmount, null);
    }

    /**
     * Lowers the pending amount of a slot to what is still in it, removing the slot when nothing
     * brewed is left. When {@code current} is given and is not the brewed item, the slot is dropped.
     */
    private void reducePending(StandKey standKey, int slot, int remainingAmount, ItemStack current) {
        pendingOutputs.asMap().computeIfPresent(standKey, (key, outputs) -> {
            PendingBrewOutput output = outputs.get(slot);
            if (output == null) {
                return outputs;
            }

            boolean stillBrewedItem = current == null || output.item().isSimilar(current);
            int amount = stillBrewedItem ? Math.min(output.amount(), remainingAmount) : 0;
            if (amount == output.amount()) {
                return outputs;
            }

            Map<Integer, PendingBrewOutput> updated = new HashMap<>(outputs);
            if (amount > 0) {
                updated.put(slot, new PendingBrewOutput(output.itemKey(), output.item(), amount));
            } else {
                updated.remove(slot);
            }
            return updated.isEmpty() ? null : Map.copyOf(updated);
        });
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

    private int matchingAmount(ItemStack item, PendingBrewOutput output) {
        if (item == null || !output.item().isSimilar(item)) {
            return 0;
        }
        return item.getAmount();
    }

    private Optional<String> itemTypeKey(@Nullable ItemStack item) {
        if (item == null) {
            return Optional.empty();
        }
        Material material = item.getType();
        if (!material.isItem() || material.isAir()) {
            return Optional.empty();
        }
        return Optional.ofNullable(material.asItemType())
                .map(itemType -> itemType.getKey().toString());
    }

    public record BrewClick(StandKey standKey, int slot, Inventory inventory) {
    }

    public record BrewedTake(String itemKey, int amount) {

        private static BrewedTake none() {
            return new BrewedTake("", 0);
        }

        public boolean present() {
            return amount > 0 && !itemKey.isBlank();
        }
    }

    public record StandKey(UUID worldId, int x, int y, int z) {

        private static StandKey from(Block block) {
            return new StandKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        private static Optional<StandKey> from(Inventory inventory) {
            if (!(inventory instanceof BrewerInventory brewerInventory)) {
                return Optional.empty();
            }
            BrewingStand holder = brewerInventory.getHolder();
            if (holder == null) {
                return Optional.empty();
            }
            return Optional.of(new StandKey(holder.getWorld().getUID(), holder.getX(), holder.getY(), holder.getZ()));
        }
    }

    private record PendingBrewOutput(String itemKey, ItemStack item, int amount) {
    }
}
