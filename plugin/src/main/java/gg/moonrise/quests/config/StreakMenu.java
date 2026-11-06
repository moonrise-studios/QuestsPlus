package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class StreakMenu {
    @Comment({
            "",
            "MiniMessage title for the streak menu."
    })
    private String title = "<dark_gray>Quest Streaks";
    @Comment({
            "",
            "Number of inventory rows in the streak menu."
    })
    private int rows = 6;
    @Comment({
            "",
            "Slots used to render streak milestones in order."
    })
    private List<Integer> milestoneSlots = List.of(18, 19, 28, 37, 38, 39, 30, 21, 22, 23, 32, 41, 42, 43, 34, 25, 26);
    @Comment({
            "",
            "Item rendered in unused streak menu slots."
    })
    private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
    @Comment({
            "",
            "Status item showing current streak, highest streak, and daily progress."
    })
    private MenuItem statusItem = new MenuItem(
            "CLOCK",
            "<gold>Current Streak",
            List.of(
                    "<gray>Current: <white><streak></white>",
                    "<gray>Highest: <white><highest_streak></white>",
                    "<gray>Today: <white><daily_completed></white>/<white><daily_required></white>"
            )
    );
    @Comment({
            "",
            "Slot for the streak status item."
    })
    private int statusSlot = 4;
    @Comment({
            "",
            "Informational item showing available Streak Shields. Shields are consumed automatically when a missed reset would break the streak."
    })
    private MenuItem shieldButton = new MenuItem(
            "SHIELD",
            "<aqua>Streak Shields",
            List.of(
                    "<gray>Balance: <white><shield_balance></white>",
                    "<gray>Automatically protects missed quest windows."
            )
    );
    @Comment({
            "",
            "Slot for the Streak Shield button."
    })
    private int shieldSlot = 45;
    @Comment({
            "",
            "Button that opens the Streak Recovery confirmation menu."
    })
    private MenuItem recoveryButton = new MenuItem(
            "TOTEM_OF_UNDYING",
            "<green>Recover Lost Streak",
            List.of(
                    "<gray>Balance: <white><recovery_balance></white>",
                    "<gray>Last Lost: <white><last_lost_streak></white>",
                    "<gray>Recovery Days Left: <white><recovery_days_remaining></white>",
                    "<yellow>Click to recover if available."
            )
    );
    @Comment({
            "",
            "Slot for the Streak Recovery button."
    })
    private int recoverySlot = 53;
    @Comment({
            "",
            "Back button used to return from the streak menu."
    })
    private BackButton backButton = new BackButton();
    @Comment({
            "",
            "Item template for streak milestones the player has not reached."
    })
    private StreakMilestoneItem lockedMilestone = new StreakMilestoneItem(
            "GRAY_DYE",
            "<gray><milestone_display_name>",
            List.of("<streak_locked>", "<streak_progress>")
    );
    @Comment({
            "",
            "Item template for streak milestones already reached and rewarded."
    })
    private StreakMilestoneItem claimedMilestone = new StreakMilestoneItem(
            "EMERALD",
            "<green><milestone_display_name>",
            List.of("<streak_completed>", "<streak_progress>")
    );
    @Comment({
            "",
            "Confirmation menu used before applying recovery."
    })
    private ConfirmationMenu confirmation = new ConfirmationMenu();
}
