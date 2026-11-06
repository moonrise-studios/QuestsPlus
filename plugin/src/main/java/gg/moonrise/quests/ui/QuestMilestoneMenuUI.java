package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.MilestoneMenu;
import gg.moonrise.quests.core.service.MenuTokens;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestMilestone;
import org.bukkit.entity.Player;

import java.util.List;

public class QuestMilestoneMenuUI extends ChestMenu {

    public QuestMilestoneMenuUI(Player player, QuestMenuService menuService, PlayerQuestState state, QuestDifficulty difficulty) {
        this(player, menuService, state, difficulty, 0);
    }

    public QuestMilestoneMenuUI(Player player, QuestMenuService menuService, PlayerQuestState state, QuestDifficulty difficulty, int page) {
        super(player, title(menuService, difficulty), rows(menuService.config().getMilestoneMenu()));

        MilestoneMenu menu = menuService.config().getMilestoneMenu();
        int totalSlots = MenuSlots.totalSlots(rows(menu));
        int safePage = Math.max(0, page);
        MenuSlots menuSlots = new MenuSlots(this::addButton, totalSlots);

        List<Integer> milestoneSlots = menu.getMilestoneSlots();
        List<QuestMilestone> milestones = difficulty.milestones();
        int pageSize = Math.max(1, milestoneSlots.size());
        int startIndex = safePage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, milestones.size());
        for (int index = startIndex; index < endIndex; index++) {
            QuestMilestone milestone = milestones.get(index);
            int slot = milestoneSlots.get(index - startIndex);
            if (slot >= totalSlots) {
                continue;
            }
            menuSlots.add(slot, Button.builder()
                    .item(viewer -> menuService.items().buildMilestoneItem(state, difficulty, milestone))
                    .build());
        }

        if (safePage > 0 && menu.getPreviousPageSlot() < totalSlots) {
            int previousPage = safePage - 1;
            menuSlots.add(menu.getPreviousPageSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildTokenItem(
                            menu.getPreviousPageButton(),
                            new MenuTokens()
                                    .putNumber("page", safePage + 1)
                                    .putNumber("previous_page", previousPage + 1)
                                    .putNumber("next_page", safePage + 2),
                            org.bukkit.Material.ARROW
                    ))
                    .action((button, clicker, event) -> new QuestMilestoneMenuUI(clicker, menuService, state, difficulty, previousPage).open())
                    .build());
        }

        if (menu.getBackButton().isEnabled() && menu.getBackButton().getSlot() < totalSlots) {
            menuSlots.add(menu.getBackButton().getSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildTokenItem(
                            menu.getBackButton().getItem(),
                            new MenuTokens().putNumber("page", safePage + 1),
                            org.bukkit.Material.ARROW
                    ))
                    .action((button, clicker, event) -> menuService.openMilestoneSelector(clicker))
                    .build());
        }

        if (endIndex < milestones.size() && menu.getNextPageSlot() < totalSlots) {
            int nextPage = safePage + 1;
            menuSlots.add(menu.getNextPageSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildTokenItem(
                            menu.getNextPageButton(),
                            new MenuTokens()
                                    .putNumber("page", safePage + 1)
                                    .putNumber("previous_page", safePage)
                                    .putNumber("next_page", nextPage + 1),
                            org.bukkit.Material.ARROW
                    ))
                    .action((button, clicker, event) -> new QuestMilestoneMenuUI(clicker, menuService, state, difficulty, nextPage).open())
                    .build());
        }

        menuSlots.fill(menuService, menu.getFiller());
    }

    private static String title(QuestMenuService menuService, QuestDifficulty difficulty) {
        return menuService.config().getMilestoneMenu().getMilestonesTitle()
                .replace("<quest_difficulty>", difficulty.displayName())
                .replace("<difficulty>", difficulty.displayName())
                .replace("<difficulty_id>", difficulty.id());
    }

    private static int rows(MilestoneMenu menu) {
        return MenuSlots.clampRows(menu.getRows());
    }
}
