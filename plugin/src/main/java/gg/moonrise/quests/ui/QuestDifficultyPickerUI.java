package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.BackButton;
import gg.moonrise.quests.config.DifficultyPicker;
import gg.moonrise.quests.core.service.MenuTokens;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.domain.model.QuestDifficulty;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuestDifficultyPickerUI extends ChestMenu {

    public QuestDifficultyPickerUI(Player player, QuestMenuService menuService, int slotIndex, boolean reroll) {
        super(player, menuService.config().getMenu().getDifficultyPicker().getTitle(), rows(menuService.config().getMenu().getDifficultyPicker()));

        DifficultyPicker picker = menuService.config().getMenu().getDifficultyPicker();
        int totalSlots = MenuSlots.totalSlots(rows(picker));
        MenuSlots menuSlots = new MenuSlots(this::addButton, totalSlots);

        BackButton backButton = picker.getBackButton();
        Set<Integer> reservedSlots = backButton != null && backButton.isEnabled() && backButton.getSlot() >= 0 && backButton.getSlot() < totalSlots
                ? Set.of(backButton.getSlot())
                : Set.of();
        List<QuestDifficulty> difficulties = menuService.items().difficulties();
        Map<QuestDifficulty, Integer> difficultySlots = DifficultySelectorSlots.resolve(
                difficulties,
                QuestDifficulty::pickerSlot,
                picker.getSlots(),
                totalSlots,
                reservedSlots
        );
        for (Map.Entry<QuestDifficulty, Integer> entry : difficultySlots.entrySet()) {
            QuestDifficulty difficulty = entry.getKey();
            menuSlots.add(entry.getValue(), Button.builder()
                    .item(viewer -> menuService.items().buildDifficultyPickerItem(difficulty, slotIndex))
                    .action((button, clicker, event) -> menuService.selectDifficultyForSlot(clicker, slotIndex, difficulty.id(), reroll))
                    .build());
        }

        if (backButton != null && backButton.isEnabled() && backButton.getSlot() >= 0 && backButton.getSlot() < totalSlots) {
            menuSlots.add(backButton.getSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildTokenItem(backButton.getItem(), new MenuTokens(), Material.ARROW))
                    .action((button, clicker, event) -> menuService.openDailyQuests(clicker))
                    .build());
        }

        menuSlots.fill(menuService, picker.getFiller());
    }

    private static int rows(DifficultyPicker picker) {
        return MenuSlots.clampRows(picker.getRows());
    }
}
