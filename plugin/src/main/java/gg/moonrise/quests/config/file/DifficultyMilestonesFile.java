package gg.moonrise.quests.config.file;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;
import gg.moonrise.quests.config.ConfigDefaults;
import gg.moonrise.quests.config.quest.MilestoneConfig;

@Getter
@Configuration
public class DifficultyMilestonesFile {
    @Comment({
            "",
            "Quest completion milestones for this difficulty."
    })
    private List<MilestoneConfig> milestones = ConfigDefaults.easyMilestones();
}
