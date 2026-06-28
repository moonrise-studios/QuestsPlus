package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.engine.paper.item.ItemBuilder;
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
        int totalSlots = rows(menu) * 9;
        int globalSlot = menuService.globalQuestSlot();
        List<Integer> contentSlots = sanitizeContentSlots(menu.getContentSlots(), totalSlots, globalSlot);
        Set<Integer> occupiedSlots = new HashSet<>();

        if (globalSlot >= 0 && globalSlot < totalSlots) {
            addTrackedButton(occupiedSlots, totalSlots, globalSlot, Button.builder()
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
                addTrackedButton(occupiedSlots, totalSlots, inventorySlot, Button.builder()
                        .item(viewer -> menuService.buildLockedPremiumQuestSlotItem(dailySlot))
                        .build());
                continue;
            }
            if (quest == null) {
                addTrackedButton(occupiedSlots, totalSlots, inventorySlot, Button.builder()
                        .item(viewer -> menuService.buildEmptyQuestSlotItem(dailySlot))
                        .action((button, clicker, event) -> menuService.openDifficultyPicker(clicker, dailySlot))
                        .build());
                continue;
            }
            addTrackedButton(occupiedSlots, totalSlots, inventorySlot, Button.builder()
                    .item(viewer -> menuService.buildQuestItem(viewer, quest))
                    .action((button, clicker, event) -> menuService.openRerollDifficultyPicker(clicker, dailySlot))
                    .build());
        }

        if (menuService.canShowMilestoneButton()) {
            int slot = menu.getMilestoneButton().getSlot();
            if (slot >= 0 && slot < totalSlots) {
                addTrackedButton(occupiedSlots, totalSlots, slot, Button.builder()
                        .item(viewer -> menuService.buildMenuItem(menu.getMilestoneButton().getItem()))
                        .action((button, clicker, event) -> menuService.openMilestoneSelector(clicker))
                        .build());
            }
        }

        if (menuService.canShowStreakButton()) {
            int slot = menu.getStreakButton().getSlot();
            if (slot >= 0 && slot < totalSlots) {
                addTrackedButton(occupiedSlots, totalSlots, slot, Button.builder()
                        .item(viewer -> menuService.buildMenuItem(menu.getStreakButton().getItem()))
                        .action((button, clicker, event) -> menuService.openStreaks(clicker))
                        .build());
            }
        }

        if (menuService.canShowQuestResetButton()) {
            int slot = menu.getResetButton().getSlot();
            if (slot >= 0 && slot < totalSlots) {
                addTrackedButton(occupiedSlots, totalSlots, slot, Button.builder()
                        .item(viewer -> menuService.buildQuestResetButtonItem(viewer, state))
                        .action((button, clicker, event) -> menuService.openQuestResetPurchase(clicker))
                        .build());
            }
        }

        addFillers(menuService, menu.getFiller(), totalSlots, occupiedSlots);
    }

    private static int rows(Config.QuestMenu menu) {
        return Math.max(1, Math.min(6, menu.getRows()));
    }

    private static List<Integer> sanitizeContentSlots(List<Integer> configuredSlots, int totalSlots, int reservedSlot) {
        List<Integer> sanitized = new ArrayList<>(configuredSlots.stream()
                .filter(slot -> slot >= 0 && slot < totalSlots && slot != reservedSlot)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll));

        if (!sanitized.isEmpty()) {
            return sanitized;
        }

        return IntStream.range(0, totalSlots)
                .filter(slot -> slot != reservedSlot)
                .boxed()
                .toList();
    }

    private Button fillerButton(QuestMenuService menuService, Config.MenuItem item) {
        return Button.builder()
                .item(player -> ItemBuilder.of(menuService.buildMenuItem(item)).hideToolTip(true).build())
                .build();
    }

    private void addTrackedButton(Set<Integer> occupiedSlots, int totalSlots, int slot, Button button) {
        if (slot < 0 || slot >= totalSlots) {
            return;
        }
        addButton(slot, button);
        occupiedSlots.add(slot);
    }

    private void addFillers(QuestMenuService menuService, Config.MenuItem item, int totalSlots, Set<Integer> occupiedSlots) {
        IntStream.range(0, totalSlots)
                .filter(slot -> !occupiedSlots.contains(slot))
                .forEach(slot -> addButton(slot, fillerButton(menuService, item)));
    }
}
