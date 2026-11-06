package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class QuestScheduleConfig {
    @Comment({
            "",
            "Optional inclusive begin timestamp. Leave fields blank for no lower bound."
    })
    private QuestScheduleBoundConfig begin = new QuestScheduleBoundConfig();
    @Comment({
            "",
            "Optional exclusive end timestamp. Leave fields blank for no upper bound."
    })
    private QuestScheduleBoundConfig end = new QuestScheduleBoundConfig();
}
