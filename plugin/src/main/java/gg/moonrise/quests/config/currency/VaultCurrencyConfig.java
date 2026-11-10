package gg.moonrise.quests.config.currency;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;
import gg.moonrise.quests.config.menu.MenuButton;
import gg.moonrise.quests.config.menu.MenuItem;

@Getter
@Configuration
public class VaultCurrencyConfig {
    @Comment({
            "",
            "Display name used by menus and messages for this currency."
    })
    private String displayName = "Ingame Money";
    @Comment({
            "",
            "Vault economy amount charged when purchasing a Quest Reset."
    })
    private double questResetCost = 1000.0D;
    @Comment({
            "",
            "Button for purchasing a Quest Reset with Vault economy. Supports <completed>, <required>, <status>, <payment>, <reward>, <amount>, <resets_used>, <resets_limit>, and <resets_remaining>."
    })
    private MenuButton button = new MenuButton(15, new MenuItem(
            "EMERALD",
            "<green>Ingame Money",
            List.of(
                    "<gray>Cost: <white><amount></white>",
                    "<gray>Resets left: <white><resets_remaining></white>/<white><resets_limit></white>",
                    "<gray>Resets <white><completed></white>/<white><required></white> completed quests.",
                    "<yellow>Click to buy and reset."
            )
    ));

    public VaultCurrencyConfig() {
    }

    public VaultCurrencyConfig(String displayName, double questResetCost, MenuButton button) {
        this.displayName = displayName;
        this.questResetCost = questResetCost;
        this.button = button;
    }
}
