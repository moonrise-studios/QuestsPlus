package gg.moonrise.quests.config.quest;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class GlobalSchedule {
    @Comment({
            "",
            "Day of week for this weekly boundary. Use names like FRIDAY."
    })
    private String dayOfWeek = "FRIDAY";
    @Comment({
            "",
            "Server-local boundary time in HH:mm format."
    })
    private String time = "05:00";
}
