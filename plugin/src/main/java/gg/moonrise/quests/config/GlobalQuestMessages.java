package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import gg.moonrise.engine.message.Message;
import lombok.Getter;

@Getter
@Configuration
public class GlobalQuestMessages {
    @Comment({
            "",
            "Broadcast sent when a new global quest period starts."
    })
    private Message periodStarted = Message.of("<green>Global Quest Started: <white><quest_display_name></white>");
    @Comment({
            "",
            "Broadcast sent when a global quest period ends."
    })
    private Message periodEnded = Message.of("<gold>The global quest period has ended.");
    @Comment({
            "",
            "Message sent when no active global quest can receive progress."
    })
    private Message noActiveQuest = Message.of("<yellow>No active global quest was progressed.");
    @Comment({
            "",
            "Admin confirmation when global quest admin progress is applied."
    })
    private Message commandProgress = Message.of("<green>Added <white><amount></white> progress to the active global <white><quest_type></white> quest for <white><player></white>.");
    @Comment({
            "",
            "Admin message when the active global quest does not match the requested quest type."
    })
    private Message commandNoActive = Message.of("<yellow>No active global <white><quest_type></white> quest was progressed for <white><player></white>.");
    @Comment({
            "",
            "Message sent when an admin provides an invalid global quest type."
    })
    private Message invalidQuestType = Message.of("<red>Invalid global quest type: <white><quest_type></white>.");
}
