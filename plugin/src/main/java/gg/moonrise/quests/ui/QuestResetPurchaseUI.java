package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.engine.paper.item.ItemBuilder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

public class QuestResetPurchaseUI extends ChestMenu {

    public QuestResetPurchaseUI(Player player, QuestMenuService menuService, QuestResetEligibility eligibility) {
        super(player, menuService.config().getMenu().getResetMenu().getTitle(), rows(menuService.config().getMenu().getResetMenu()));

        Config.QuestResetMenu menu = menuService.config().getMenu().getResetMenu();
        int totalSlots = rows(menu) * 9;
        Set<Integer> occupiedSlots = new HashSet<>();

        for (QuestCurrency currency : menuService.questResetCurrencies()) {
            addPurchaseButton(menuService, occupiedSlots, totalSlots, currency, eligibility);
        }

        Config.BackButton backButton = menu.getBackButton();
        if (backButton != null && backButton.isEnabled()) {
            addTrackedButton(occupiedSlots, totalSlots, backButton.getSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(backButton.getItem(), new QuestMenuService.MapTokens(), Material.ARROW))
                    .action((button, clicker, event) -> menuService.openDailyQuests(clicker))
                    .build());
        }

        addFillers(menuService, menu.getFiller(), totalSlots, occupiedSlots);
    }

    private void addPurchaseButton(
            QuestMenuService menuService,
            Set<Integer> occupiedSlots,
            int totalSlots,
            QuestCurrency currency,
            QuestResetEligibility eligibility
    ) {
        QuestCurrencyButton button = menuService.questResetPaymentButton(currency);
        if (button == null) {
            return;
        }
        if (!menuService.canShowQuestResetPayment(currency)) {
            return;
        }
        addTrackedButton(occupiedSlots, totalSlots, button.slot(), Button.builder()
                .item(viewer -> menuService.buildQuestResetPurchaseItem(viewer, button, currency, eligibility))
                .action((guiButton, clicker, event) -> menuService.applyQuestResetPurchase(clicker, currency.key()))
                .build());
    }

    private static int rows(Config.QuestResetMenu menu) {
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
