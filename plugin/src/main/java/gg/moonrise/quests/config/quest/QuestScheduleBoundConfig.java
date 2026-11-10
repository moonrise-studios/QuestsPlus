package gg.moonrise.quests.config.quest;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class QuestScheduleBoundConfig {
    @Comment({
            "",
            "Date in day/month/year format, for example 09/05/2026."
    })
    private String date = "";
    @Comment({
            "",
            "Time in 24-hour HH:mm format, for example 05:00."
    })
    private String time = "";
    @Comment({
            "",
            "Java timezone id for this timestamp, for example America/Halifax or UTC."
    })
    private String timezone = "";
}
