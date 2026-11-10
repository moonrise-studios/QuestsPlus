package gg.moonrise.quests.config.menu;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class StreakButton {
    @Comment({
            "",
            "Whether the daily quest menu shows the streak button."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Slot for the streak button in the daily quest menu."
    })
    private int slot = 26;
    @Comment({
            "",
            "Item template for the streak button."
    })
    private MenuItem item = new MenuItem(
            "BLAZE_POWDER",
            "<gold>Quest Streaks",
            List.of("<gray>View streaks, shields, and recovery.")
    );
}
