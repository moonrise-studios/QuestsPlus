package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.model.PlayerQuestState;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class QuestMenuUI extends ChestMenu {

    public QuestMenuUI(Player player, QuestMenuService menuService, PlayerQuestState state, GlobalQuestState globalState) {
        super(player, menuService.config().getMenu().getTitle(), rows(menuService.config().getMenu()));

        Config.QuestMenu menu = menuService.config().getMenu();
        int totalSlots = MenuSlots.totalSlots(rows(menu));
        int globalSlot = menuService.globalQuestSlot();
        Set<Integer> reservedSlots = reservedSlots(menuService, menu, totalSlots, globalSlot);
        List<Integer> contentSlots = sanitizeContentSlots(menu.getContentSlots(), totalSlots, reservedSlots);
        MenuSlots menuSlots = new MenuSlots(this::addButton, totalSlots);

        if (globalSlot >= 0 && globalSlot < totalSlots) {
            menuSlots.add(globalSlot, Button.builder()
                    .item(viewer -> menuService.buildGlobalQuestItem(viewer, globalState))
                    .action((button, clicker, event) -> menuService.openGlobalRewardPreview(clicker))
                    .build());
        }

        int questSlotCount = Math.min(contentSlots.size(), menuService.totalVisibleQuestSlots());
        for (int slotIndex = 0; slotIndex < questSlotCount; slotIndex++) {
            GeneratedQuest quest = state.questAtSlot(slotIndex);
            int inventorySlot = contentSlots.get(slotIndex);
            int dailySlot = slotIndex;
            if (!menuService.canAccessSlot(player, dailySlot)) {
                menuSlots.add(inventorySlot, Button.builder()
                        .item(viewer -> menuService.buildLockedPremiumQuestSlotItem(dailySlot))
                        .build());
                continue;
            }
            if (quest == null) {
                menuSlots.add(inventorySlot, Button.builder()
                        .item(viewer -> menuService.buildEmptyQuestSlotItem(dailySlot))
                        .action((button, clicker, event) -> menuService.openDifficultyPicker(clicker, dailySlot))
                        .build());
                continue;
            }
            menuSlots.add(inventorySlot, Button.builder()
                    .item(viewer -> menuService.buildQuestItem(viewer, quest))
                    .action((button, clicker, event) -> menuService.openRerollDifficultyPicker(clicker, dailySlot))
                    .build());
        }

        if (menuService.canShowMilestoneButton()) {
            int slot = menu.getMilestoneButton().getSlot();
            if (slot >= 0 && slot < totalSlots) {
                menuSlots.add(slot, Button.builder()
                        .item(viewer -> menuService.buildMenuItem(menu.getMilestoneButton().getItem()))
                        .action((button, clicker, event) -> menuService.openMilestoneSelector(clicker))
                        .build());
            }
        }

        if (menuService.canShowStreakButton()) {
            int slot = menu.getStreakButton().getSlot();
            if (slot >= 0 && slot < totalSlots) {
                menuSlots.add(slot, Button.builder()
                        .item(viewer -> menuService.buildMenuItem(menu.getStreakButton().getItem()))
                        .action((button, clicker, event) -> menuService.openStreaks(clicker))
                        .build());
            }
        }

        if (menuService.canShowQuestResetButton()) {
            int slot = menu.getResetButton().getSlot();
            if (slot >= 0 && slot < totalSlots) {
                menuSlots.add(slot, Button.builder()
                        .item(viewer -> menuService.buildQuestResetButtonItem(viewer, state))
                        .action((button, clicker, event) -> menuService.openQuestResetPurchase(clicker))
                        .build());
            }
        }

        menuSlots.fill(menuService, menu.getFiller());
    }

    private static int rows(Config.QuestMenu menu) {
        return MenuSlots.clampRows(menu.getRows());
    }

    private static Set<Integer> reservedSlots(QuestMenuService menuService, Config.QuestMenu menu, int totalSlots, int globalSlot) {
        Set<Integer> reserved = new HashSet<>();
        addReservedSlot(reserved, globalSlot, totalSlots);
        if (menuService.canShowMilestoneButton()) {
            addReservedSlot(reserved, menu.getMilestoneButton().getSlot(), totalSlots);
        }
        if (menuService.canShowStreakButton()) {
            addReservedSlot(reserved, menu.getStreakButton().getSlot(), totalSlots);
        }
        if (menuService.canShowQuestResetButton()) {
            addReservedSlot(reserved, menu.getResetButton().getSlot(), totalSlots);
        }
        return reserved;
    }

    private static void addReservedSlot(Set<Integer> reservedSlots, int slot, int totalSlots) {
        if (slot >= 0 && slot < totalSlots) {
            reservedSlots.add(slot);
        }
    }

    private static List<Integer> sanitizeContentSlots(List<Integer> configuredSlots, int totalSlots, Set<Integer> reservedSlots) {
        List<Integer> safeConfiguredSlots = configuredSlots == null ? List.of() : configuredSlots;
        List<Integer> sanitized = new ArrayList<>(safeConfiguredSlots.stream()
                .filter(slot -> slot >= 0 && slot < totalSlots && !reservedSlots.contains(slot))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll));

        if (!sanitized.isEmpty()) {
            return sanitized;
        }

        return IntStream.range(0, totalSlots)
                .filter(slot -> !reservedSlots.contains(slot))
                .boxed()
                .toList();
    }
}
