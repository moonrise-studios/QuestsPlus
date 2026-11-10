package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.ChestMenu;
import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.menu.BackButton;
import gg.moonrise.quests.config.menu.QuestResetMenu;
import gg.moonrise.quests.entity.QuestResetEligibility;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Optional;

public class QuestResetPurchaseUI extends ChestMenu {

    public QuestResetPurchaseUI(Player player, QuestMenuService menuService, QuestResetEligibility eligibility) {
        super(player, menuService.config().getMenu().getResetMenu().getTitle(), rows(menuService.config().getMenu().getResetMenu()));

        QuestResetMenu menu = menuService.config().getMenu().getResetMenu();
        int totalSlots = MenuSlots.totalSlots(rows(menu));
        MenuSlots menuSlots = new MenuSlots(this::addButton, totalSlots);

        for (QuestCurrency currency : menuService.currencies().questResetCurrencies()) {
            addPurchaseButton(menuService, menuSlots, currency, eligibility);
        }

        BackButton backButton = menu.getBackButton();
        if (backButton != null && backButton.isEnabled()) {
            menuSlots.add(backButton.getSlot(), Button.builder()
                    .item(viewer -> menuService.items().buildTokenItem(backButton.getItem(), new MenuTokens(), Material.ARROW))
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
        Optional<QuestCurrencyButton> paymentButton = menuService.currencies().questResetPaymentButton(currency);
        if (paymentButton.isEmpty() || !menuService.currencies().canShowQuestResetPayment(currency)) {
            return;
        }
        QuestCurrencyButton button = paymentButton.get();
        menuSlots.add(button.slot(), Button.builder()
                .item(viewer -> menuService.items().buildQuestResetPurchaseItem(viewer, button, currency, eligibility))
                .action((guiButton, clicker, event) -> menuService.applyQuestResetPurchase(clicker, currency.key()))
                .build());
    }

    private static int rows(QuestResetMenu menu) {
        return MenuSlots.clampRows(menu.getRows());
    }
}
