package gg.moonrise.quests.config.quest;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class StreakMilestoneConfig {
    @Comment({
            "",
            "Streak length required to trigger this milestone."
    })
    private int streak;
    @Comment({
            "",
            "MiniMessage display name for this streak milestone."
    })
    private String displayName;
    @Comment({
            "",
            "Extra MiniMessage lore lines appended to this streak milestone's GUI item."
    })
    private List<String> lore = List.of();
    @Comment({
            "",
            "Console commands run once when this streak milestone is reached."
    })
    private List<String> commands;

    public StreakMilestoneConfig() {
    }

    public StreakMilestoneConfig(int streak, String displayName, List<String> commands) {
        this(streak, displayName, List.of(), commands);
    }

    public StreakMilestoneConfig(int streak, String displayName, List<String> lore, List<String> commands) {
        this.streak = streak;
        this.displayName = displayName;
        this.lore = lore;
        this.commands = commands;
    }
}
