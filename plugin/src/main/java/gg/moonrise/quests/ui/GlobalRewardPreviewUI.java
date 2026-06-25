package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.engine.paper.item.ItemBuilder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class GlobalRewardPreviewUI extends ChestMenu {

    public GlobalRewardPreviewUI(Player player, QuestMenuService menuService) {
        super(player, menuService.config().getGlobalQuests().getMenu().getRewardPreview().getTitle(), rows(menuService.config().getGlobalQuests().getMenu().getRewardPreview()));

        Config.GlobalRewardPreviewMenu menu = menuService.config().getGlobalQuests().getMenu().getRewardPreview();
        int totalSlots = rows(menu) * 9;
        Set<Integer> occupiedSlots = new HashSet<>();

        addRewardTierItems(menuService, occupiedSlots, totalSlots, menu.getFullRewardSlots(), menuService.fullGlobalRewardTiers(), false);
        addRewardTierItems(menuService, occupiedSlots, totalSlots, menu.getReducedRewardSlots(), menuService.reducedGlobalRewardTiers(), true);

        Config.BackButton backButton = menu.getBackButton();
        if (backButton != null && backButton.isEnabled()) {
            addTrackedButton(occupiedSlots, totalSlots, backButton.getSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(backButton.getItem(), new QuestMenuService.MapTokens(), Material.ARROW))
                    .action((button, clicker, event) -> menuService.openDailyQuests(clicker))
                    .build());
        }

        addFillers(menuService, menu.getFiller(), totalSlots, occupiedSlots);
    }

    private void addRewardTierItems(
            QuestMenuService menuService,
            Set<Integer> occupiedSlots,
            int totalSlots,
            List<Integer> slots,
            List<Config.GlobalRewardTierConfig> tiers,
            boolean reduced
    ) {
        int count = Math.min(slots.size(), tiers.size());
        for (int index = 0; index < count; index++) {
            int slot = slots.get(index);
            Config.GlobalRewardTierConfig tier = tiers.get(index);
            addTrackedButton(occupiedSlots, totalSlots, slot, Button.builder()
                    .item(viewer -> menuService.buildGlobalRewardPreviewItem(tier, reduced))
                    .build());
        }
    }

    private static int rows(Config.GlobalRewardPreviewMenu menu) {
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
