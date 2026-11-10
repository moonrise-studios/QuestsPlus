package gg.moonrise.quests.presentation.menu;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.menu.StreakMenu;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.domain.model.QuestStreakMilestone;
import gg.moonrise.quests.domain.model.QuestStreakState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class QuestStreakMenuUI extends ChestMenu {

    public QuestStreakMenuUI(Player player, QuestMenuService menuService, PlayerQuestState questState, QuestStreakState streakState) {
        super(player, menuService.config().getStreaks().getMenu().getTitle(), rows(menuService.config().getStreaks().getMenu()));

        StreakMenu menu = menuService.config().getStreaks().getMenu();
        int totalSlots = MenuSlots.totalSlots(rows(menu));
        MenuSlots menuSlots = new MenuSlots(this::addButton, totalSlots);

        if (menu.getStatusSlot() >= 0 && menu.getStatusSlot() < totalSlots) {
            menuSlots.add(menu.getStatusSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildStreakStatusItem(viewer, questState, streakState))
                    .build());
        }

        if (menu.getShieldSlot() >= 0 && menu.getShieldSlot() < totalSlots) {
            menuSlots.add(menu.getShieldSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildStreakShieldButton(viewer, questState, streakState))
                    .build());
        }

        if (menu.getRecoverySlot() >= 0 && menu.getRecoverySlot() < totalSlots) {
            menuSlots.add(menu.getRecoverySlot(), Button.builder()
                    .item(viewer -> menuService.items().buildStreakRecoveryButton(viewer, questState, streakState))
                    .action((button, clicker, event) -> menuService.openStreakConfirmation(clicker))
                    .build());
        }

        if (menu.getBackButton().isEnabled() && menu.getBackButton().getSlot() >= 0 && menu.getBackButton().getSlot() < totalSlots) {
            menuSlots.add(menu.getBackButton().getSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildTokenItem(menu.getBackButton().getItem(), menuService.items().streakTokens(viewer, questState, streakState), Material.ARROW))
                    .action((button, clicker, event) -> menuService.openDailyQuests(clicker))
                    .build());
        }

        List<Integer> slots = menu.getMilestoneSlots();
        List<QuestStreakMilestone> milestones = menuService.items().streakMilestones();
        int count = Math.min(slots.size(), milestones.size());
        for (int index = 0; index < count; index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= totalSlots) {
                continue;
            }
            QuestStreakMilestone milestone = milestones.get(index);
            menuSlots.add(slot, Button.builder()
                    .item(viewer -> menuService.items().buildStreakMilestoneItem(viewer, questState, streakState, milestone))
                    .build());
        }

        menuSlots.fill(menuService, menu.getFiller());
    }

    private static int rows(StreakMenu menu) {
        return MenuSlots.clampRows(menu.getRows());
    }
}
