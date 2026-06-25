package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.engine.paper.item.ItemBuilder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestStreakState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

public class QuestStreakConfirmUI extends ChestMenu {

    public QuestStreakConfirmUI(Player player, QuestMenuService menuService, PlayerQuestState questState, QuestStreakState streakState) {
        super(player, title(menuService), rows(menuService.config().getStreaks().getMenu().getConfirmation()));

        Config.ConfirmationMenu menu = menuService.config().getStreaks().getMenu().getConfirmation();
        int totalSlots = rows(menu) * 9;
        Set<Integer> occupiedSlots = new HashSet<>();

        if (menu.getConfirmSlot() >= 0 && menu.getConfirmSlot() < totalSlots) {
            addTrackedButton(occupiedSlots, totalSlots, menu.getConfirmSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(menu.getConfirmButton(), menuService.streakTokens(viewer, questState, streakState), Material.LIME_CONCRETE))
                    .action((button, clicker, event) -> menuService.applyStreakRecovery(clicker))
                    .build());
        }

        if (menu.getCancelSlot() >= 0 && menu.getCancelSlot() < totalSlots) {
            addTrackedButton(occupiedSlots, totalSlots, menu.getCancelSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(menu.getCancelButton(), menuService.streakTokens(viewer, questState, streakState), Material.RED_CONCRETE))
                    .action((button, clicker, event) -> menuService.openStreaks(clicker))
                    .build());
        }

        addFillers(menuService, menu.getFiller(), totalSlots, occupiedSlots);
    }

    private static String title(QuestMenuService menuService) {
        Config.ConfirmationMenu menu = menuService.config().getStreaks().getMenu().getConfirmation();
        return menu.getRecoveryTitle();
    }

    private static int rows(Config.ConfirmationMenu menu) {
        return Math.max(1, Math.min(6, menu.getRows()));
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
