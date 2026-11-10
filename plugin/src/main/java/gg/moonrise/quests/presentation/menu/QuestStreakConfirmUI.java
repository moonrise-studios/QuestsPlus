package gg.moonrise.quests.presentation.menu;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.ConfirmationMenu;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.domain.model.QuestStreakState;
import org.bukkit.Material;
import org.bukkit.entity.Player;


public class QuestStreakConfirmUI extends ChestMenu {

    public QuestStreakConfirmUI(Player player, QuestMenuService menuService, PlayerQuestState questState, QuestStreakState streakState) {
        super(player, title(menuService), rows(menuService.config().getStreaks().getMenu().getConfirmation()));

        ConfirmationMenu menu = menuService.config().getStreaks().getMenu().getConfirmation();
        int totalSlots = MenuSlots.totalSlots(rows(menu));
        MenuSlots menuSlots = new MenuSlots(this::addButton, totalSlots);

        if (menu.getConfirmSlot() >= 0 && menu.getConfirmSlot() < totalSlots) {
            menuSlots.add(menu.getConfirmSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildTokenItem(menu.getConfirmButton(), menuService.items().streakTokens(viewer, questState, streakState), Material.LIME_CONCRETE))
                    .action((button, clicker, event) -> menuService.applyStreakRecovery(clicker))
                    .build());
        }

        if (menu.getCancelSlot() >= 0 && menu.getCancelSlot() < totalSlots) {
            menuSlots.add(menu.getCancelSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildTokenItem(menu.getCancelButton(), menuService.items().streakTokens(viewer, questState, streakState), Material.RED_CONCRETE))
                    .action((button, clicker, event) -> menuService.openStreaks(clicker))
                    .build());
        }

        menuSlots.fill(menuService, menu.getFiller());
    }

    private static String title(QuestMenuService menuService) {
        ConfirmationMenu menu = menuService.config().getStreaks().getMenu().getConfirmation();
        return menu.getRecoveryTitle();
    }

    private static int rows(ConfirmationMenu menu) {
        return MenuSlots.clampRows(menu.getRows());
    }
}
