package gg.moonrise.quests.config.message;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import gg.moonrise.engine.message.Message;
import lombok.Getter;

@Getter
@Configuration
public class MilestoneMessages {
    @Comment({
            "",
            "Total completed quest count message."
    })
    private Message completedCount = Message.of("<green>You have completed <white><quests_completed></white> quests across all difficulties.");
    @Comment({
            "",
            "Per-difficulty completed quest summary line."
    })
    private Message completedDifficultyLine = Message.of("<gray>- <quest_difficulty><gray>: <white><quests_completed></white>");
    @Comment({
            "",
            "Message sent when a quest completion unlocks a completion milestone.",
            "Supports <quest_display_name>, <quest_difficulty>, <difficulty>, <difficulty_id>, <completed>, <milestone_completed>, and <milestone_display_name>."
    })
    private Message milestoneCompleted = Message.of("<green>Milestone complete: <white><milestone_display_name></white> <gray>(<quest_difficulty>, <milestone_completed> quests).");
    @Comment({
            "",
            "Message sent when an existing completion milestone is rewarded retroactively.",
            "Supports <quest_difficulty>, <difficulty>, <difficulty_id>, <completed>, <milestone_completed>, and <milestone_display_name>."
    })
    private Message milestoneClaimed = Message.of("<green>Milestone reward claimed: <white><milestone_display_name></white> <gray>(<quest_difficulty>, <milestone_completed> quests).");

    public MilestoneMessages() {
    }

    public MilestoneMessages(Message milestoneCompleted, Message milestoneClaimed) {
        this.milestoneCompleted = milestoneCompleted;
        this.milestoneClaimed = milestoneClaimed;
    }
}
