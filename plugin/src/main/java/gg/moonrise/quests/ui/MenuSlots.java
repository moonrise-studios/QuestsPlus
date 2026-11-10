package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.engine.paper.item.ItemBuilder;
import gg.moonrise.quests.config.menu.MenuItem;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

/**
 * Places buttons into a chest menu while tracking which slots are taken, so the remaining
 * slots can be filled afterwards. Each menu creates one instance bound to its own
 * {@code addButton} method.
 */
public final class MenuSlots {

    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 6;
    private static final int SLOTS_PER_ROW = 9;

    private final BiConsumer<Integer, Button> buttonSink;
    private final int totalSlots;
    private final Set<Integer> occupiedSlots = new HashSet<>();

    public MenuSlots(@NotNull BiConsumer<Integer, Button> buttonSink, int totalSlots) {
        this.buttonSink = buttonSink;
        this.totalSlots = totalSlots;
    }

    public static int clampRows(int configuredRows) {
        return Math.clamp(configuredRows, MIN_ROWS, MAX_ROWS);
    }

    public static int totalSlots(int rows) {
        return rows * SLOTS_PER_ROW;
    }

    /**
     * Places the button and marks its slot as occupied. Slots outside the menu are ignored.
     */
    public void add(int slot, @NotNull Button button) {
        if (slot < 0 || slot >= totalSlots) {
            return;
        }
        buttonSink.accept(slot, button);
        occupiedSlots.add(slot);
    }

    /**
     * Places a filler button in every slot that no {@link #add(int, Button)} call has taken.
     */
    public void fill(@NotNull QuestMenuService menuService, @NotNull MenuItem item) {
        IntStream.range(0, totalSlots)
                .filter(slot -> !occupiedSlots.contains(slot))
                .forEach(slot -> buttonSink.accept(slot, fillerButton(menuService, item)));
    }

    private static Button fillerButton(QuestMenuService menuService, MenuItem item) {
        return Button.builder()
                .item(player -> ItemBuilder.of(menuService.items().buildMenuItem(item)).hideToolTip(true).build())
                .build();
    }
}
