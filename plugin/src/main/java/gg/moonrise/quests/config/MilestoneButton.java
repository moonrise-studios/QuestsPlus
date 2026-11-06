package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class MilestoneButton {
    @Comment({
            "",
            "Whether the daily quest menu shows the milestone button."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Slot for the milestone button in the daily quest menu."
    })
    private int slot = 22;
    @Comment({
            "",
            "Item template for the milestone button."
    })
    private MenuItem item = new MenuItem(
            "NETHER_STAR",
            "<gold>Quest Milestones",
            List.of("<gray>View quest completion milestones.")
    );
}
