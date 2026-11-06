package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Configuration
public class QuestDifficultyConfig {
    @Comment({
            "",
            "MiniMessage display name shown for this difficulty."
    })
    private String displayName = "<green><b>EASY";
    @Comment({
            "",
            "MiniMessage lore lines inserted by <difficulty_lore> in quest menu items."
    })
    private List<String> lore = List.of();
    @Comment({
            "",
            "Inventory slot for this difficulty in the daily difficulty picker; -1 uses the menu slot order."
    })
    private int pickerSlot = -1;
    @Comment({
            "",
            "Inventory slot for this difficulty in the milestone difficulty selector; -1 uses the menu slot order."
    })
    private int milestonesSlot = -1;
    @Comment({
            "",
            "Completed quest requirements needed before players can select this difficulty.",
            "Keys are difficulty ids and values are the number of completed quests needed for that difficulty."
    })
    private Map<String, Integer> requirements = Map.of();
    @Comment({
            "",
            "Random baseline reward command pool; one command is selected when a quest of this difficulty is completed."
    })
    private Rewards rewards = new Rewards(List.of("eco give <player> 250"));
    @Comment({
            "",
            "Composed milestone list for this difficulty; source milestones live in difficulty/<id>/milestones.yml."
    })
    private List<MilestoneConfig> milestones = List.of();

    public QuestDifficultyConfig() {
    }

    public QuestDifficultyConfig(String displayName, Rewards rewards) {
        this.displayName = displayName;
        this.rewards = rewards;
    }

    public QuestDifficultyConfig(String displayName, List<String> lore, Rewards rewards) {
        this.displayName = displayName;
        this.lore = lore;
        this.rewards = rewards;
    }

    public QuestDifficultyConfig(String displayName, List<String> lore, int pickerSlot, int milestonesSlot, Rewards rewards) {
        this.displayName = displayName;
        this.lore = lore;
        this.pickerSlot = pickerSlot;
        this.milestonesSlot = milestonesSlot;
        this.rewards = rewards;
    }

    public QuestDifficultyConfig(
            String displayName,
            List<String> lore,
            int pickerSlot,
            int milestonesSlot,
            Map<String, Integer> requirements,
            Rewards rewards,
            List<MilestoneConfig> milestones
    ) {
        this(displayName, lore, pickerSlot, milestonesSlot, rewards);
        this.requirements = requirements;
        this.milestones = milestones;
    }
}
