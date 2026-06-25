package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.engine.paper.item.ItemBuilder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestStreakMilestone;
import gg.moonrise.quests.model.QuestStreakState;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class QuestStreakMenuUI extends ChestMenu {

    public QuestStreakMenuUI(Player player, QuestMenuService menuService, PlayerQuestState questState, QuestStreakState streakState) {
        super(player, menuService.config().getStreaks().getMenu().getTitle(), rows(menuService.config().getStreaks().getMenu()));

        Config.StreakMenu menu = menuService.config().getStreaks().getMenu();
        int totalSlots = rows(menu) * 9;
        Set<Integer> occupiedSlots = new HashSet<>();

        if (menu.getStatusSlot() >= 0 && menu.getStatusSlot() < totalSlots) {
            addTrackedButton(occupiedSlots, totalSlots, menu.getStatusSlot(), Button.builder()
                    .item(viewer -> menuService.buildStreakStatusItem(viewer, questState, streakState))
                    .build());
        }

        if (menu.getShieldSlot() >= 0 && menu.getShieldSlot() < totalSlots) {
            addTrackedButton(occupiedSlots, totalSlots, menu.getShieldSlot(), Button.builder()
                    .item(viewer -> menuService.buildStreakShieldButton(viewer, questState, streakState))
                    .build());
        }

        if (menu.getRecoverySlot() >= 0 && menu.getRecoverySlot() < totalSlots) {
            addTrackedButton(occupiedSlots, totalSlots, menu.getRecoverySlot(), Button.builder()
                    .item(viewer -> menuService.buildStreakRecoveryButton(viewer, questState, streakState))
                    .action((button, clicker, event) -> menuService.openStreakConfirmation(clicker))
                    .build());
        }

        if (menu.getBackButton().isEnabled() && menu.getBackButton().getSlot() >= 0 && menu.getBackButton().getSlot() < totalSlots) {
            addTrackedButton(occupiedSlots, totalSlots, menu.getBackButton().getSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(menu.getBackButton().getItem(), menuService.streakTokens(viewer, questState, streakState), Material.ARROW))
                    .action((button, clicker, event) -> menuService.openDailyQuests(clicker))
                    .build());
        }

        List<Integer> slots = menu.getMilestoneSlots();
        List<QuestStreakMilestone> milestones = menuService.streakMilestones();
        int count = Math.min(slots.size(), milestones.size());
        for (int index = 0; index < count; index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= totalSlots) {
                continue;
            }
            QuestStreakMilestone milestone = milestones.get(index);
            addTrackedButton(occupiedSlots, totalSlots, slot, Button.builder()
                    .item(viewer -> menuService.buildStreakMilestoneItem(viewer, questState, streakState, milestone))
                    .build());
        }

        addFillers(menuService, menu.getFiller(), totalSlots, occupiedSlots);
    }

    private static int rows(Config.StreakMenu menu) {
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
