package gg.moonrise.quests.config.quest;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class MilestoneConfig {
    @Comment({
            "",
            "Per-difficulty completed quest count required to trigger this milestone."
    })
    private int completed = 5;
    @Comment({
            "",
            "MiniMessage display name for this quest completion milestone."
    })
    private String displayName = "<green>Milestone";
    @Comment({
            "",
            "Extra MiniMessage lore lines appended to this quest completion milestone's GUI item."
    })
    private List<String> lore = List.of();
    @Comment({
            "",
            "Console commands run once when this quest completion milestone is reached."
    })
    private List<String> commands = List.of();

    public MilestoneConfig() {
    }

    public MilestoneConfig(int completed, String displayName, List<String> commands) {
        this(completed, displayName, List.of(), commands);
    }

    public MilestoneConfig(int completed, String displayName, List<String> lore, List<String> commands) {
        this.completed = completed;
        this.displayName = displayName;
        this.lore = lore;
        this.commands = commands;
    }
}
