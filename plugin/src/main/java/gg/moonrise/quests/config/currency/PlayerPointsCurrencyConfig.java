package gg.moonrise.quests.config.currency;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;
import gg.moonrise.quests.config.menu.MenuButton;
import gg.moonrise.quests.config.menu.MenuItem;

@Getter
@Configuration
public class PlayerPointsCurrencyConfig {
    @Comment({
            "",
            "Display name used by menus and messages for this currency."
    })
    private String displayName = "Player Points";
    @Comment({
            "",
            "PlayerPoints amount charged when purchasing a Quest Reset."
    })
    private int questResetCost = 25;
    @Comment({
            "",
            "Button for purchasing a Quest Reset with PlayerPoints. Supports <completed>, <required>, <status>, <payment>, <reward>, <amount>, <resets_used>, <resets_limit>, and <resets_remaining>."
    })
    private MenuButton button = new MenuButton(11, new MenuItem(
            "SUNFLOWER",
            "<gold>Player Points",
            List.of(
                    "<gray>Cost: <white><amount></white> points",
                    "<gray>Resets left: <white><resets_remaining></white>/<white><resets_limit></white>",
                    "<gray>Resets <white><completed></white>/<white><required></white> completed quests.",
                    "<yellow>Click to buy and reset."
            )
    ));

    public PlayerPointsCurrencyConfig() {
    }

    public PlayerPointsCurrencyConfig(String displayName, int questResetCost, MenuButton button) {
        this.displayName = displayName;
        this.questResetCost = questResetCost;
        this.button = button;
    }
}
