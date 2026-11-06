package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The quest slot layout of the daily quest menu, derived from one {@link Config} instance. It is the single source
 * of truth for which inventory slots hold quests, used both for slot access rules and to render the menu.
 * Computing it walks the menu config, so it is built once per config (see {@link QuestService#menuLayout()})
 * instead of on every progress event.
 * <p>
 * A utility button's slot is reserved whenever the button is enabled in the config, even if the menu ends up not
 * showing it (the reset button is hidden while no reset currency is available). Currency availability is runtime
 * state, so reserving by config keeps the quest slots stable and computable once per config.
 *
 * @param totalSlots          inventory size of the menu
 * @param globalQuestSlot     inventory slot of the global quest item; may lie outside a small menu
 * @param reservedSlots       inventory slots taken by utility buttons, never used for quests
 * @param contentSlots        inventory slot of each quest slot index, in order
 * @param dailyQuestSlots     number of regular (non-premium) quest slots
 * @param visiblePremiumSlots number of premium quest slots shown to anyone, the highest configured premium limit
 */
public record QuestMenuLayout(
        int totalSlots,
        int globalQuestSlot,
        @NotNull Set<Integer> reservedSlots,
        @NotNull List<Integer> contentSlots,
        int dailyQuestSlots,
        int visiblePremiumSlots
) {

    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 6;
    private static final int SLOTS_PER_ROW = 9;
    private static final int DEFAULT_GLOBAL_QUEST_SLOT = 15;
    private static final int MAX_INVENTORY_SLOT = MAX_ROWS * SLOTS_PER_ROW - 1;

    public QuestMenuLayout {
        reservedSlots = Collections.unmodifiableSet(new LinkedHashSet<>(reservedSlots));
        contentSlots = List.copyOf(contentSlots);
    }

    public static QuestMenuLayout of(@NotNull Config config) {
        Config.QuestMenu menu = config.getMenu();
        int totalSlots = Math.clamp(menu.getRows(), MIN_ROWS, MAX_ROWS) * SLOTS_PER_ROW;
        int globalQuestSlot = globalQuestSlot(config);
        Set<Integer> reservedSlots = reservedSlots(config, menu, totalSlots, globalQuestSlot);
        List<Integer> contentSlots = contentSlots(menu.getContentSlots(), totalSlots, reservedSlots);
        int capacity = contentSlots.size();
        int dailyQuestSlots = capacity <= 0 ? 0 : Math.clamp(config.getDaily().getQuestCount(), 1, capacity);
        int visiblePremiumSlots = Math.clamp(PermissionLimits.premiumSlots(config).highestConfigured(), 0, Math.max(0, capacity - dailyQuestSlots));
        return new QuestMenuLayout(totalSlots, globalQuestSlot, reservedSlots, contentSlots, dailyQuestSlots, visiblePremiumSlots);
    }

    /**
     * The configured global quest slot, clamped to the largest possible chest menu.
     */
    public static int globalQuestSlot(@NotNull Config config) {
        Config.GlobalQuestMenu menu = config.getGlobalQuests().getMenu();
        return menu == null ? DEFAULT_GLOBAL_QUEST_SLOT : Math.clamp(menu.getSlot(), 0, MAX_INVENTORY_SLOT);
    }

    public int rows() {
        return totalSlots / SLOTS_PER_ROW;
    }

    /**
     * Number of quest slots the menu can hold.
     */
    public int capacity() {
        return contentSlots.size();
    }

    /**
     * Number of quest slots shown in the menu: the daily slots plus every premium slot anyone can unlock.
     */
    public int totalVisibleQuestSlots() {
        return Math.min(capacity(), dailyQuestSlots + visiblePremiumSlots);
    }

    public boolean isPremiumSlot(int slotIndex) {
        return slotIndex >= dailyQuestSlots;
    }

    private static Set<Integer> reservedSlots(Config config, Config.QuestMenu menu, int totalSlots, int globalQuestSlot) {
        Set<Integer> reserved = new LinkedHashSet<>();
        addReservedSlot(reserved, globalQuestSlot, totalSlots);
        Config.MilestoneButton milestoneButton = menu.getMilestoneButton();
        if (config.isQuestMilestonesEnabled() && milestoneButton != null && milestoneButton.isEnabled()) {
            addReservedSlot(reserved, milestoneButton.getSlot(), totalSlots);
        }
        Config.Streaks streaks = config.getStreaks();
        Config.StreakButton streakButton = menu.getStreakButton();
        if (streaks != null && streaks.isEnabled() && streakButton != null && streakButton.isEnabled()) {
            addReservedSlot(reserved, streakButton.getSlot(), totalSlots);
        }
        Config.QuestResetButton resetButton = menu.getResetButton();
        if (resetButton != null && resetButton.isEnabled()) {
            addReservedSlot(reserved, resetButton.getSlot(), totalSlots);
        }
        return reserved;
    }

    private static void addReservedSlot(Set<Integer> reservedSlots, int slot, int totalSlots) {
        if (slot >= 0 && slot < totalSlots) {
            reservedSlots.add(slot);
        }
    }

    /**
     * The configured content slots that fit the menu and are not reserved, or every free slot if none remain.
     */
    private static List<Integer> contentSlots(List<Integer> configuredSlots, int totalSlots, Set<Integer> reservedSlots) {
        Set<Integer> configured = new LinkedHashSet<>();
        for (Integer slot : configuredSlots == null ? List.<Integer>of() : configuredSlots) {
            if (slot != null && slot >= 0 && slot < totalSlots && !reservedSlots.contains(slot)) {
                configured.add(slot);
            }
        }
        if (!configured.isEmpty()) {
            return new ArrayList<>(configured);
        }

        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < totalSlots; slot++) {
            if (!reservedSlots.contains(slot)) {
                free.add(slot);
            }
        }
        return free;
    }
}
