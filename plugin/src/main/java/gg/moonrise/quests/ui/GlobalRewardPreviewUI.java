package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class GlobalRewardPreviewUI extends ChestMenu {

    public GlobalRewardPreviewUI(Player player, QuestMenuService menuService) {
        super(player, menuService.config().getGlobalQuests().getMenu().getRewardPreview().getTitle(), rows(menuService.config().getGlobalQuests().getMenu().getRewardPreview()));

        Config.GlobalRewardPreviewMenu menu = menuService.config().getGlobalQuests().getMenu().getRewardPreview();
        int totalSlots = MenuSlots.totalSlots(rows(menu));
        MenuSlots menuSlots = new MenuSlots(this::addButton, totalSlots);

        addRewardTierItems(menuService, menuSlots, menu.getFullRewardSlots(), menuService.fullGlobalRewardTiers(), false);
        addRewardTierItems(menuService, menuSlots, menu.getReducedRewardSlots(), menuService.reducedGlobalRewardTiers(), true);

        Config.BackButton backButton = menu.getBackButton();
        if (backButton != null && backButton.isEnabled()) {
            menuSlots.add(backButton.getSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(backButton.getItem(), new QuestMenuService.MapTokens(), Material.ARROW))
                    .action((button, clicker, event) -> menuService.openDailyQuests(clicker))
                    .build());
        }

        menuSlots.fill(menuService, menu.getFiller());
    }

    private void addRewardTierItems(
            QuestMenuService menuService,
            MenuSlots menuSlots,
            List<Integer> slots,
            List<Config.GlobalRewardTierConfig> tiers,
            boolean reduced
    ) {
        int count = Math.min(slots.size(), tiers.size());
        for (int index = 0; index < count; index++) {
            int slot = slots.get(index);
            Config.GlobalRewardTierConfig tier = tiers.get(index);
            menuSlots.add(slot, Button.builder()
                    .item(viewer -> menuService.buildGlobalRewardPreviewItem(tier, reduced))
                    .build());
        }
    }

    private static int rows(Config.GlobalRewardPreviewMenu menu) {
        return MenuSlots.clampRows(menu.getRows());
    }
}
