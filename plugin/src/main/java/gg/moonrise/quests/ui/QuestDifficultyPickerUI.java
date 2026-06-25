package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.engine.paper.item.ItemBuilder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.model.QuestDifficulty;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public class QuestDifficultyPickerUI extends ChestMenu {

    public QuestDifficultyPickerUI(Player player, QuestMenuService menuService, int slotIndex, boolean reroll) {
        super(player, menuService.config().getMenu().getDifficultyPicker().getTitle(), rows(menuService.config().getMenu().getDifficultyPicker()));

        Config.DifficultyPicker picker = menuService.config().getMenu().getDifficultyPicker();
        int totalSlots = rows(picker) * 9;
        Set<Integer> occupiedSlots = new HashSet<>();

        Config.BackButton backButton = picker.getBackButton();
        Set<Integer> reservedSlots = backButton != null && backButton.isEnabled() && backButton.getSlot() >= 0 && backButton.getSlot() < totalSlots
                ? Set.of(backButton.getSlot())
                : Set.of();
        List<QuestDifficulty> difficulties = menuService.difficulties();
        Map<QuestDifficulty, Integer> difficultySlots = DifficultySelectorSlots.resolve(
                difficulties,
                QuestDifficulty::pickerSlot,
                picker.getSlots(),
                totalSlots,
                reservedSlots
        );
        for (Map.Entry<QuestDifficulty, Integer> entry : difficultySlots.entrySet()) {
            QuestDifficulty difficulty = entry.getKey();
            addTrackedButton(occupiedSlots, totalSlots, entry.getValue(), Button.builder()
                    .item(viewer -> menuService.buildDifficultyPickerItem(difficulty, slotIndex))
                    .action((button, clicker, event) -> menuService.selectDifficultyForSlot(clicker, slotIndex, difficulty.id(), reroll))
                    .build());
        }

        if (backButton != null && backButton.isEnabled() && backButton.getSlot() >= 0 && backButton.getSlot() < totalSlots) {
            addTrackedButton(occupiedSlots, totalSlots, backButton.getSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(backButton.getItem(), new QuestMenuService.MapTokens(), org.bukkit.Material.ARROW))
                    .action((button, clicker, event) -> menuService.openDailyQuests(clicker))
                    .build());
        }

        addFillers(menuService, picker.getFiller(), totalSlots, occupiedSlots);
    }

    private static int rows(Config.DifficultyPicker picker) {
        return Math.max(1, Math.min(6, picker.getRows()));
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
