package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Optional;

public class QuestResetPurchaseUI extends ChestMenu {

    public QuestResetPurchaseUI(Player player, QuestMenuService menuService, QuestResetEligibility eligibility) {
        super(player, menuService.config().getMenu().getResetMenu().getTitle(), rows(menuService.config().getMenu().getResetMenu()));

        Config.QuestResetMenu menu = menuService.config().getMenu().getResetMenu();
        int totalSlots = MenuSlots.totalSlots(rows(menu));
        MenuSlots menuSlots = new MenuSlots(this::addButton, totalSlots);

        for (QuestCurrency currency : menuService.questResetCurrencies()) {
            addPurchaseButton(menuService, menuSlots, currency, eligibility);
        }

        Config.BackButton backButton = menu.getBackButton();
        if (backButton != null && backButton.isEnabled()) {
            menuSlots.add(backButton.getSlot(), Button.builder()
                    .item(viewer -> menuService.buildTokenItem(backButton.getItem(), new QuestMenuService.MapTokens(), Material.ARROW))
                    .action((button, clicker, event) -> menuService.openDailyQuests(clicker))
                    .build());
        }

        menuSlots.fill(menuService, menu.getFiller());
    }

    private void addPurchaseButton(
            QuestMenuService menuService,
            MenuSlots menuSlots,
            QuestCurrency currency,
            QuestResetEligibility eligibility
    ) {
        Optional<QuestCurrencyButton> paymentButton = menuService.questResetPaymentButton(currency);
        if (paymentButton.isEmpty() || !menuService.canShowQuestResetPayment(currency)) {
            return;
        }
        QuestCurrencyButton button = paymentButton.get();
        menuSlots.add(button.slot(), Button.builder()
                .item(viewer -> menuService.buildQuestResetPurchaseItem(viewer, button, currency, eligibility))
                .action((guiButton, clicker, event) -> menuService.applyQuestResetPurchase(clicker, currency.key()))
                .build());
    }

    private static int rows(Config.QuestResetMenu menu) {
        return MenuSlots.clampRows(menu.getRows());
    }
}
