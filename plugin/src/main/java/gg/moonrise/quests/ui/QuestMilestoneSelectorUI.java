package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.BackButton;
import gg.moonrise.quests.config.MilestoneMenu;
import gg.moonrise.quests.core.service.MenuTokens;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.domain.model.QuestDifficulty;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuestMilestoneSelectorUI extends ChestMenu {

    public QuestMilestoneSelectorUI(Player player, QuestMenuService menuService, PlayerQuestState state) {
        super(player, menuService.config().getMilestoneMenu().getSelectorTitle(), selectorRows(menuService.config().getMilestoneMenu()));

        MilestoneMenu menu = menuService.config().getMilestoneMenu();
        int totalSlots = MenuSlots.totalSlots(selectorRows(menu));
        MenuSlots menuSlots = new MenuSlots(this::addButton, totalSlots);

        BackButton backButton = menu.getSelectorBackButton();
        boolean showBackButton = backButton != null && backButton.isEnabled() && backButton.getSlot() >= 0 && backButton.getSlot() < totalSlots;
        Set<Integer> reservedSlots = showBackButton ? Set.of(backButton.getSlot()) : Set.of();
        List<QuestDifficulty> difficulties = menuService.items().difficulties();
        Map<QuestDifficulty, Integer> selectorSlots = DifficultySelectorSlots.resolve(
                difficulties,
                QuestDifficulty::milestonesSlot,
                menu.getSelectorSlots(),
                totalSlots,
                reservedSlots
        );
        for (Map.Entry<QuestDifficulty, Integer> entry : selectorSlots.entrySet()) {
            QuestDifficulty difficulty = entry.getKey();
            menuSlots.add(entry.getValue(), Button.builder()
                    .item(viewer -> menuService.items().buildDifficultyItem(state, difficulty))
                    .action((button, clicker, event) -> menuService.openMilestones(clicker, difficulty.id()))
                    .build());
        }

        if (showBackButton) {
            menuSlots.add(backButton.getSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildTokenItem(backButton.getItem(), new MenuTokens(), Material.ARROW))
                    .action((button, clicker, event) -> menuService.openDailyQuests(clicker))
                    .build());
        }

        menuSlots.fill(menuService, menu.getFiller());
    }

    private static int selectorRows(MilestoneMenu menu) {
        return MenuSlots.clampRows(menu.getSelectorRows());
    }
}
