package gg.moonrise.quests.config.menu;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class QuestResetMenu {
    @Comment({
            "",
            "MiniMessage title for the quest reset purchase choice menu."
    })
    private String title = "<dark_gray>Buy Quest Reset";
    @Comment({
            "",
            "Number of inventory rows in the quest reset purchase choice menu."
    })
    private int rows = 3;
    @Comment({
            "",
            "Item rendered in unused reset purchase menu slots."
    })
    private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
    @Comment({
            "",
            "Maximum quest reset purchases each player may make per active reset window.",
            "Values below 0 are treated as 0."
    })
    private int dailyLimit = 1;
    @Comment({
            "",
            "Status text used for <status> when the player may purchase a Quest Reset."
    })
    private String statusReady = "Ready";
    @Comment({
            "",
            "Status text used for <status> when the player has not completed every accessible quest."
    })
    private String statusIncomplete = "Complete all quests";
    @Comment({
            "",
            "Status text used for <status> when the player has no Quest Reset purchases remaining."
    })
    private String statusLimitReached = "You have already used your resets for the day";
    @Comment({
            "",
            "Back button used to return from the reset purchase choice menu to daily quests."
    })
    private BackButton backButton = new BackButton(22, new MenuItem("ARROW", "<yellow>Back", List.of("<gray>Return to quests.")));

    public QuestResetMenu() {
    }

    public QuestResetMenu(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public QuestResetMenu(String statusReady, String statusIncomplete, String statusLimitReached) {
        this.statusReady = statusReady;
        this.statusIncomplete = statusIncomplete;
        this.statusLimitReached = statusLimitReached;
    }
}
