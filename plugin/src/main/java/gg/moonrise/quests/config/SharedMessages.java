package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import gg.moonrise.engine.message.Message;
import lombok.Getter;

@Getter
@Configuration
public class SharedMessages {
    @Comment({
            "",
            "Player command usage message."
    })
    private Message usage = Message.of("<yellow>/quests</yellow> <gray>or</gray> <yellow>/q</yellow> <gray>- Open daily quests\n<yellow>/quests progress</yellow> <gray>or</gray> <yellow>/q progress</yellow> <gray>- View progress\n<yellow>/quests completed</yellow> <gray>or</gray> <yellow>/q completed</yellow> <gray>- View completed quest totals\n<yellow>/quests milestones</yellow> <gray>or</gray> <yellow>/q milestones</yellow> <gray>- View completion milestones\n<yellow>/quests streaks</yellow> <gray>or</gray> <yellow>/q streaks</yellow> <gray>- View quest streaks\n<yellow>/quests indicator <main|global|both> <type|off|default></yellow> <gray>or</gray> <yellow>/q indicator <main|global|both> <type|off|default></yellow> <gray>- Set progress indicators</gray>");
    @Comment({
            "",
            "Admin command usage message."
    })
    private Message adminUsage = Message.of("<yellow>/questsadmin reload</yellow> <gray>or</gray> <yellow>/qa reload</yellow>\n<yellow>/questsadmin listtypes</yellow> <gray>or</gray> <yellow>/qa listtypes</yellow>\n<yellow>/questsadmin reset <player></yellow> <gray>or</gray> <yellow>/qa reset <player></yellow>\n<yellow>/questsadmin complete <player></yellow> <gray>or</gray> <yellow>/qa complete <player></yellow>\n<yellow>/questsadmin dailyrerolls reset <player></yellow> <gray>or</gray> <yellow>/qa dailyrerolls reset <player></yellow>\n<yellow>/questsadmin add <amount> <quest-type> <player></yellow> <gray>or</gray> <yellow>/qa add <amount> <quest-type> <player></yellow>\n<yellow>/questsadmin global add <quest-type> <amount> <player></yellow> <gray>or</gray> <yellow>/qa global add <quest-type> <amount> <player></yellow>\n<yellow>/questsadmin global refresh</yellow> <gray>or</gray> <yellow>/qa global refresh</yellow>\n<yellow>/questsadmin shield give <amount> <player></yellow> <gray>or</gray> <yellow>/qa shield give <amount> <player></yellow>\n<yellow>/questsadmin shield take <amount> <player></yellow> <gray>or</gray> <yellow>/qa shield take <amount> <player></yellow>\n<yellow>/questsadmin recovery give <amount> <player></yellow> <gray>or</gray> <yellow>/qa recovery give <amount> <player></yellow>\n<yellow>/questsadmin recovery take <amount> <player></yellow> <gray>or</gray> <yellow>/qa recovery take <amount> <player></yellow>");
    @Comment({
            "",
            "Message sent after a successful /questsadmin reload or /qa reload."
    })
    private Message reloadSuccess = Message.of("<green>QuestsPlus config reloaded.");
    @Comment({
            "",
            "Message sent when /questsadmin reload or /qa reload fails."
    })
    private Message reloadFailed = Message.of("<red>QuestsPlus config reload failed. Check console for details.");
    @Comment({
            "",
            "Message sent when a player-only command is used by console."
    })
    private Message playerOnly = Message.of("<red>Only players can use this command.");
    @Comment({
            "",
            "Message sent when a player selects a quest progress indicator."
    })
    private Message indicatorSelected = Message.of("<green>Quest progress indicator set to <white><indicator></white>.");
    @Comment({
            "",
            "Message sent when a player resets their quest progress indicator to the default BossBar."
    })
    private Message indicatorReset = Message.of("<green>Quest progress indicator reset to <white>BossBar</white>.");
    @Comment({
            "",
            "Message sent when a requested quest progress indicator is unknown or unavailable."
    })
    private Message indicatorUnavailable = Message.of("<red>Unknown or unavailable quest progress indicator: <white><indicator></white>.");
    @Comment({
            "",
            "Message sent when a player disables a quest progress indicator scope."
    })
    private Message indicatorDisabled = Message.of("<green><scope> quest progress indicator disabled.");
    @Comment({
            "",
            "Admin confirmation when quest progress is applied."
    })
    private Message adminProgress = Message.of("<green>Added <white><amount></white> progress to <white><quest_id></white> for <white><player></white>.");
    @Comment({
            "",
            "Admin message when quest progress does not apply to an active quest."
    })
    private Message adminProgressNotActive = Message.of("<yellow>No active quest <white><quest_id></white> was progressed for <white><player></white>.");
    @Comment({
            "",
            "Message sent when an admin command amount is not positive."
    })
    private Message invalidAdminProgressAmount = Message.of("<red>Amount must be a positive integer.");
    @Comment({
            "",
            "Message sent when an admin command quest type is unknown."
    })
    private Message invalidQuestType = Message.of("<red>Invalid quest type: <white><quest_type></white>.");
    @Comment({
            "",
            "Message sent when an online player argument cannot be resolved."
    })
    private Message playerNotFound = Message.of("<red>Player not found: <white><player></white>.");
    @Comment({
            "",
            "Admin confirmation after refreshing the active global quest."
    })
    private Message globalQuestRefreshed = Message.of("<green>Refreshed the active global quest.");
    @Comment({
            "",
            "Admin confirmation after resetting a player's daily reroll usage."
    })
    private Message rerollsReset = Message.of("<green>Reset daily rerolls for <white><player></white>.");
    @Comment({
            "",
            "Message sent when the daily quest menu cannot be opened."
    })
    private Message menuOpenFailed = Message.of("<red>Failed to open quests right now.");
    @Comment({
            "",
            "Message sent when a player opens quest milestones while they are disabled."
    })
    private Message questMilestonesDisabled = Message.of("<red>Quest milestones are disabled.");
    @Comment({
            "",
            "Message sent when the quest milestone selector or milestone page cannot be opened."
    })
    private Message milestonesOpenFailed = Message.of("<red>Failed to open quest milestones right now.");
    @Comment({
            "",
            "Message sent when a requested quest difficulty does not exist."
    })
    private Message unknownQuestDifficulty = Message.of("<red>Unknown quest difficulty.");
    @Comment({
            "",
            "Message sent when a player uses quest streaks while they are disabled."
    })
    private Message questStreaksDisabled = Message.of("<red>Quest streaks are disabled.");
    @Comment({
            "",
            "Message sent when the quest streak menu cannot be opened."
    })
    private Message streaksOpenFailed = Message.of("<red>Failed to open quest streaks right now.");
    @Comment({
            "",
            "Message sent when the quest streak confirmation menu cannot be opened."
    })
    private Message streakConfirmationOpenFailed = Message.of("<red>Failed to open quest streak confirmation right now.");
    @Comment({
            "",
            "Message sent when the quest reset purchase menu cannot be opened."
    })
    private Message questResetPurchaseOpenFailed = Message.of("<red>Failed to open quest reset purchases right now.");
    @Comment({
            "",
            "Message sent when the quest difficulty picker cannot be opened."
    })
    private Message difficultyPickerOpenFailed = Message.of("<red>Failed to open quest difficulty picker right now.");
    @Comment({
            "",
            "Message sent when the quest reroll difficulty picker cannot be opened."
    })
    private Message rerollPickerOpenFailed = Message.of("<red>Failed to open quest reroll picker right now.");
    @Comment({
            "",
            "Message sent when selecting or rerolling a quest difficulty fails."
    })
    private Message difficultySelectFailed = Message.of("<red>Failed to select that quest difficulty right now.");
    @Comment({
            "",
            "Message sent when /quests progress cannot load the player's quests."
    })
    private Message progressLoadFailed = Message.of("<red>Failed to load quest progress right now.");
    @Comment({
            "",
            "Message sent when /quests completed cannot load the player's completed quest count."
    })
    private Message completedCountLoadFailed = Message.of("<red>Failed to load completed quest count right now.");
    @Comment({
            "",
            "Message sent when a progress indicator preference cannot be saved."
    })
    private Message indicatorUpdateFailed = Message.of("<red>Failed to update quest indicator preference.");
    @Comment({
            "",
            "Admin message sent when resetting a player's daily quests fails."
    })
    private Message resetPlayerFailed = Message.of("<red>Failed to reset that player's quests.");
    @Comment({
            "",
            "Admin message sent when completing a player's daily quests fails."
    })
    private Message completeDailyPlayerFailed = Message.of("<red>Failed to complete that player's daily quests.");
    @Comment({
            "",
            "Admin message sent when refreshing the active global quest fails."
    })
    private Message globalQuestRefreshFailed = Message.of("<red>Failed to refresh the active global quest.");
    @Comment({
            "",
            "Admin message sent when resetting a player's daily reroll usage fails."
    })
    private Message rerollsResetFailed = Message.of("<red>Failed to reset that player's rerolls.");
    @Comment({
            "",
            "Admin message sent when changing shield or recovery currency balances fails."
    })
    private Message streakCurrencyUpdateFailed = Message.of("<red>Failed to update streak currency.");
    @Comment({
            "",
            "Configurable streak milestone locked text."
    })
    private String streakMilestoneLocked = "<dark_gray>Locked";
    @Comment({
            "",
            "Configurable streak milestone completed text."
    })
    private String streakMilestoneCompleted = "<green>Completed";
    @Comment({
            "",
            "Configurable streak milestone progress text. Supports <streak> and <milestone_streak>."
    })
    private String streakMilestoneProgress = "<gray>Progress: <white><streak></white>/<white><milestone_streak></white>";
}
