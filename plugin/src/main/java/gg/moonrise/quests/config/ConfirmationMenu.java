package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class ConfirmationMenu {
    @Comment({
            "",
            "MiniMessage title for the Streak Recovery confirmation menu."
    })
    private String recoveryTitle = "<dark_gray>Recover Streak?";
    @Comment({
            "",
            "Number of inventory rows in confirmation menus."
    })
    private int rows = 3;
    @Comment({
            "",
            "Item rendered in unused confirmation menu slots."
    })
    private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
    @Comment({
            "",
            "Slot for the confirmation button."
    })
    private int confirmSlot = 11;
    @Comment({
            "",
            "Item template for confirming recovery use."
    })
    private MenuItem confirmButton = new MenuItem("LIME_CONCRETE", "<green>Confirm", List.of("<gray>This action consumes one currency."));
    @Comment({
            "",
            "Slot for the cancel button."
    })
    private int cancelSlot = 15;
    @Comment({
            "",
            "Item template for cancelling shield or recovery use."
    })
    private MenuItem cancelButton = new MenuItem("RED_CONCRETE", "<red>Cancel", List.of("<gray>Return to streaks."));
}
