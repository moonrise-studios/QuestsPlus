package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.QuestMenu;
import gg.moonrise.quests.core.service.QuestMenuLayout;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.domain.model.GlobalQuestState;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class QuestMenuUI extends ChestMenu {

    public QuestMenuUI(Player player, QuestMenuService menuService, PlayerQuestState state, @Nullable GlobalQuestState globalState) {
        this(player, menuService, menuService.items().menuLayout(), state, globalState);
    }

    /**
     * Places the quests using the same {@link QuestMenuLayout} that decides slot access, so the menu and the
     * service agree on which inventory slots hold quests.
     */
    private QuestMenuUI(Player player, QuestMenuService menuService, QuestMenuLayout layout, PlayerQuestState state, @Nullable GlobalQuestState globalState) {
        super(player, menuService.config().getMenu().getTitle(), layout.rows());

        QuestMenu menu = menuService.config().getMenu();
        List<Integer> contentSlots = layout.contentSlots();
        MenuSlots menuSlots = new MenuSlots(this::addButton, layout.totalSlots());

        menuSlots.add(layout.globalQuestSlot(), Button.builder()
                .item(viewer -> menuService.items().buildGlobalQuestItem(viewer, globalState))
                .action((button, clicker, event) -> menuService.openGlobalRewardPreview(clicker))
                .build());

        int questSlotCount = layout.totalVisibleQuestSlots();
        for (int slotIndex = 0; slotIndex < questSlotCount; slotIndex++) {
            Optional<GeneratedQuest> slotQuest = state.questAtSlot(slotIndex);
            int inventorySlot = contentSlots.get(slotIndex);
            int dailySlot = slotIndex;
            if (!menuService.items().canAccessSlot(player, dailySlot)) {
                menuSlots.add(inventorySlot, Button.builder()
                        .item(viewer -> menuService.items().buildLockedPremiumQuestSlotItem(dailySlot))
                        .build());
                continue;
            }
            if (slotQuest.isEmpty()) {
                menuSlots.add(inventorySlot, Button.builder()
                        .item(viewer -> menuService.items().buildEmptyQuestSlotItem(dailySlot))
                        .action((button, clicker, event) -> menuService.openDifficultyPicker(clicker, dailySlot))
                        .build());
                continue;
            }
            GeneratedQuest quest = slotQuest.get();
            menuSlots.add(inventorySlot, Button.builder()
                    .item(viewer -> menuService.items().buildQuestItem(viewer, quest))
                    .action((button, clicker, event) -> menuService.openRerollDifficultyPicker(clicker, dailySlot))
                    .build());
        }

        if (menuService.items().canShowMilestoneButton()) {
            menuSlots.add(menu.getMilestoneButton().getSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildMenuItem(menu.getMilestoneButton().getItem()))
                    .action((button, clicker, event) -> menuService.openMilestoneSelector(clicker))
                    .build());
        }

        if (menuService.items().canShowStreakButton()) {
            menuSlots.add(menu.getStreakButton().getSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildMenuItem(menu.getStreakButton().getItem()))
                    .action((button, clicker, event) -> menuService.openStreaks(clicker))
                    .build());
        }

        // The reset button's slot stays reserved by the layout even while no currency is available to show it.
        if (menuService.items().canShowQuestResetButton()) {
            menuSlots.add(menu.getResetButton().getSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildQuestResetButtonItem(viewer, state))
                    .action((button, clicker, event) -> menuService.openQuestResetPurchase(clicker))
                    .build());
        }

        menuSlots.fill(menuService, menu.getFiller());
    }
}
