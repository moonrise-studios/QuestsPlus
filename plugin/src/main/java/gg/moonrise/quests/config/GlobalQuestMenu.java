package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class GlobalQuestMenu {
    @Comment({
            "",
            "Fixed inventory slot for the global quest item in the daily quest menu."
    })
    private int slot = 15;
    @Comment({
            "",
            "Item template shown while the active global quest is incomplete."
    })
    private QuestItem activeQuest = new QuestItem(
            "NETHER_STAR",
            "<gold>Global Quest: <quest_display_name>",
            List.of(
                    "<quest_description>",
                    "",
                    "<gray>Progress: <white><global_progress></white>/<white><global_goal_amount></white> <gray>(<global_percent>%)",
                    "<gray>Your Contribution: <white><contribution></white> <gray>(<contribution_percent>%)",
                    "<gray>Rank: <white>#<global_rank></white>/<white><global_participants></white>",
                    "<gray><global_time_remaining>"
            )
    );
    @Comment({
            "",
            "Item template shown after the active global quest reaches its goal."
    })
    private QuestItem completedQuest = new QuestItem(
            "EMERALD",
            "<green>Global Quest Complete: <quest_display_name>",
            List.of(
                    "<quest_description>",
                    "",
                    "<green>Completed",
                    "<gray>Your Contribution: <white><contribution></white> <gray>(<contribution_percent>%)",
                    "<gray>Rank: <white>#<global_rank></white>/<white><global_participants></white>",
                    "<gray><global_time_remaining>"
            )
    );
    @Comment({
            "",
            "Item shown when no valid global quest definition is available for the current period."
    })
    private MenuItem noActiveQuest = new MenuItem(
            "BARRIER",
            "<red>No Global Quest",
            List.of("<gray>No global quest is active right now.")
    );
    @Comment({
            "",
            "Cosmetic reward preview opened by clicking the global quest item."
    })
    private GlobalRewardPreviewMenu rewardPreview = new GlobalRewardPreviewMenu();
}
