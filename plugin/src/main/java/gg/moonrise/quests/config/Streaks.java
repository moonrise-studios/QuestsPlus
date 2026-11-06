package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class Streaks {
    @Comment({
            "",
            "Whether quest streaks, streak milestones, shields, recovery, and streak menus are enabled."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Daily completions required to gain one streak point; -1 means all selected daily quests."
    })
    private int dailyRequiredCompletions = -1;
    @Comment({
            "",
            "Days after a streak loss during which Streak Recovery may restore it."
    })
    private int recoveryWindowDays = 3;
    @Comment({
            "",
            "Automatic streak milestone rewards keyed by required streak length."
    })
    private List<StreakMilestoneConfig> milestones = List.of(
            new StreakMilestoneConfig(3, "<green>3 Day Streak", List.of("<gray>Reward for keeping your streak alive."), List.of("eco give <player> 500")),
            new StreakMilestoneConfig(7, "<gold>7 Day Streak", List.of("<gray>One full week of completed quest windows."), List.of("eco give <player> 1500"))
    );
    @Comment({
            "",
            "Streak status, automatic shield, recovery, milestone, and confirmation menu layout."
    })
    private StreakMenu menu = new StreakMenu();

    public Streaks() {
    }

    public Streaks(boolean enabled, int dailyRequiredCompletions, int recoveryWindowDays, List<StreakMilestoneConfig> milestones, StreakMenu menu) {
        this.enabled = enabled;
        this.dailyRequiredCompletions = dailyRequiredCompletions;
        this.recoveryWindowDays = recoveryWindowDays;
        this.milestones = milestones;
        this.menu = menu;
    }
}
