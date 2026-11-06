package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class DifficultyMilestonesFile {
    @Comment({
            "",
            "Quest completion milestones for this difficulty."
    })
    private List<MilestoneConfig> milestones = ConfigDefaults.easyMilestones();
}
