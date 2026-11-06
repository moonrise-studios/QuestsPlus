package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class GlobalRewardTierConfig {
    @Comment({
            "",
            "Percentile threshold for this stacked tier. 1 means top 1%, 10 means top 10%."
    })
    private int percentile = 100;
    @Comment({
            "",
            "MiniMessage display name for this reward tier."
    })
    private String displayName = "<gray>Participation";
    @Comment({
            "",
            "Optional item override for this reward tier in the global reward preview.",
            "Supports <reward_display_name> and <reward_percentile> in name and lore.",
            "When unset, the menu reward-preview full-reward-item or reduced-reward-item template is used."
    })
    private MenuItem item;
    @Comment({
            "",
            "Console commands run for players who qualify for this tier."
    })
    private List<String> commands = List.of();

    public GlobalRewardTierConfig() {
    }

    public GlobalRewardTierConfig(int percentile, String displayName, List<String> commands) {
        this(percentile, displayName, null, commands);
    }

    public GlobalRewardTierConfig(int percentile, String displayName, MenuItem item, List<String> commands) {
        this.percentile = percentile;
        this.displayName = displayName;
        this.item = item;
        this.commands = commands;
    }
}
