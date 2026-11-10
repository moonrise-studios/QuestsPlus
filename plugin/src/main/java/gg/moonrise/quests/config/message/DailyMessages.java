package gg.moonrise.quests.config.message;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import gg.moonrise.engine.message.Message;
import lombok.Getter;

@Getter
@Configuration
public class DailyMessages {
    @Comment({
            "",
            "Message sent when a player has no selected daily quests."
    })
    private Message noQuests = Message.of("<red>No daily quests are selected.");
    @Comment({
            "",
            "Message sent when a selected difficulty has no enabled quest definitions."
    })
    private Message noAvailableQuestForDifficulty = Message.of("<red>No quests are available for difficulty <difficulty><red>.");
    @Comment({
            "",
            "Message sent when a player has not completed the configured requirements for a difficulty.",
            "Supports <difficulty>, <difficulty_id>, and <requirements>."
    })
    private Message difficultyRequirementsNotMet = Message.of("<red>You need <requirements> before selecting <difficulty><red>.");
    @Comment({
            "",
            "Message sent when a player tries to reroll without rerolls remaining."
    })
    private Message noRerollsRemaining = Message.of("<red>You do not have any daily quest rerolls remaining.");
    @Comment({
            "",
            "Message sent when a player tries to reroll a completed quest."
    })
    private Message completedQuestCannotReroll = Message.of("<red>Completed quests cannot be rerolled.");
    @Comment({
            "",
            "Message sent after a successful player quest reroll."
    })
    private Message questRerolled = Message.of("<green>Rerolled quest slot <white><slot></white>. Remaining rerolls: <white><rerolls_remaining></white>.");
    @Comment({
            "",
            "Header sent by /quests progress."
    })
    private Message progressHeader = Message.of("<gold><bold>Daily Quests</bold></gold> <gray>- reset key <white><reset_key></white>");
    @Comment({
            "",
            "Line template for each quest in /quests progress."
    })
    private Message progressLine = Message.of("<gray>- <yellow><quest_display_name></yellow>: <white><progress></white>/<white><goal_amount></white> <gray>(<status>)</gray>");
    @Comment({
            "",
            "Message sent to a player when a quest completes."
    })
    private Message questCompleted = Message.of("<green>Quest completed: <white><quest_display_name></white>.");
    @Comment({
            "",
            "Server broadcast sent when the scheduled daily reset fires."
    })
    private Message questsResetBroadcast = Message.of("<green>Daily quests have reset.");
    @Comment({
            "",
            "Admin confirmation after resetting a player's daily quest selections."
    })
    private Message resetPlayer = Message.of("<green>Reset daily quests for <white><player></white>.");
    @Comment({
            "",
            "Admin confirmation after clearing a player's daily quest selections."
    })
    private Message rerollPlayer = Message.of("<green>Rerolled daily quests for <white><player></white>.");
    @Comment({
            "",
            "Admin confirmation after force-completing a player's current accessible daily quests.",
            "Supports <player> and <quests>."
    })
    private Message completeDailyPlayer = Message.of("<green>Completed <white><quests></white> daily quests for <white><player></white>.");
    @Comment({
            "",
            "Message sent when a player has not completed every accessible quest slot for a manual reset.",
            "Supports <completed> and <required>."
    })
    private Message questResetNotReady = Message.of("<red>Complete all available quests before resetting. <gray>(<completed>/<required>)");
    @Comment({
            "",
            "Message sent when a player already has a quest reset in progress."
    })
    private Message questResetProcessing = Message.of("<yellow>Your quest reset is already being processed.");
    @Comment({
            "",
            "Message sent after a player purchases a quest reset and their current quests are cleared.",
            "Supports <payment>, <reward>, <amount>, <completed>, and <required>."
    })
    private Message questResetSuccess = Message.of("<green>Purchased a Quest Reset for <white><amount></white> <white><payment></white>. Your quests were reset.");
    @Comment({
            "",
            "Message sent when the chosen reset purchase integration is unavailable or rejects the payment.",
            "Supports <payment>, <reward>, <amount>, <completed>, and <required>."
    })
    private Message questResetPurchaseUnavailable = Message.of("<red>You cannot afford to purchase a Quest Reset.");
    @Comment({
            "",
            "Message sent when a player has reached the configured reset purchase limit for the active reset window.",
            "Supports <completed>, <required>, <resets_used>, <resets_limit>, and <resets_remaining>."
    })
    private Message questResetLimitReached = Message.of("<red>You have already used your Quest Reset for this reset window.");
    @Comment({
            "",
            "Message sent when a completed-quest reset fails."
    })
    private Message questResetFailed = Message.of("<red>Failed to reset your quests right now.");
}
