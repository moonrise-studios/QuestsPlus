package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.engine.paper.item.ItemBuilder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestMilestone;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class QuestMilestoneMenuUI extends ChestMenu {

    public QuestMilestoneMenuUI(Player player, QuestMenuService menuService, PlayerQuestState state, QuestDifficulty difficulty) {
        this(player, menuService, state, difficulty, 0);
    }

    public QuestMilestoneMenuUI(Player player, QuestMenuService menuService, PlayerQuestState state, QuestDifficulty difficulty, int page) {
        super(player, title(menuService, difficulty), rows(menuService.config().getMilestoneMenu()));

        Config.MilestoneMenu menu = menuService.config().getMilestoneMenu();
        int totalSlots = rows(menu) * 9;
        int safePage = Math.max(0, page);
        Set<Integer> occupiedSlots = new HashSet<>();

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
            addTrackedButton(occupiedSlots, totalSlots, slot, Button.builder()
                    .item(viewer -> menuService.buildMilestoneItem(state, difficulty, milestone))
                    .build());
        }

        if (safePage > 0 && menu.getPreviousPageSlot() < totalSlots) {
            int previousPage = safePage - 1;
            addTrackedButton(occupiedSlots, totalSlots, menu.getPreviousPageSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(
                            menu.getPreviousPageButton(),
                            new QuestMenuService.MapTokens()
                                    .putNumber("page", safePage + 1)
                                    .putNumber("previous_page", previousPage + 1)
                                    .putNumber("next_page", safePage + 2),
                            org.bukkit.Material.ARROW
                    ))
                    .action((button, clicker, event) -> new QuestMilestoneMenuUI(clicker, menuService, state, difficulty, previousPage).open())
                    .build());
        }

        if (menu.getBackButton().isEnabled() && menu.getBackButton().getSlot() < totalSlots) {
            addTrackedButton(occupiedSlots, totalSlots, menu.getBackButton().getSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(
                            menu.getBackButton().getItem(),
                            new QuestMenuService.MapTokens().putNumber("page", safePage + 1),
                            org.bukkit.Material.ARROW
                    ))
                    .action((button, clicker, event) -> menuService.openMilestoneSelector(clicker))
                    .build());
        }

        if (endIndex < milestones.size() && menu.getNextPageSlot() < totalSlots) {
            int nextPage = safePage + 1;
            addTrackedButton(occupiedSlots, totalSlots, menu.getNextPageSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(
                            menu.getNextPageButton(),
                            new QuestMenuService.MapTokens()
                                    .putNumber("page", safePage + 1)
                                    .putNumber("previous_page", safePage)
                                    .putNumber("next_page", nextPage + 1),
                            org.bukkit.Material.ARROW
                    ))
                    .action((button, clicker, event) -> new QuestMilestoneMenuUI(clicker, menuService, state, difficulty, nextPage).open())
                    .build());
        }

        addFillers(menuService, menu.getFiller(), totalSlots, occupiedSlots);
    }

    private static String title(QuestMenuService menuService, QuestDifficulty difficulty) {
        return menuService.config().getMilestoneMenu().getMilestonesTitle()
                .replace("<quest_difficulty>", difficulty.displayName())
                .replace("<difficulty>", difficulty.displayName())
                .replace("<difficulty_id>", difficulty.id());
    }

    private static int rows(Config.MilestoneMenu menu) {
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
