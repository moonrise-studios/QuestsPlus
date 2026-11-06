package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import gg.moonrise.engine.message.Message;
import lombok.Getter;

@Getter
@Configuration
public class StreakMessages {
    @Comment({
            "",
            "Message sent after a successful Streak Recovery."
    })
    private Message streakRecoveryApplied = Message.of("<green>Recovered your streak to <white><streak></white>.");
    @Comment({
            "",
            "Message sent when no recoverable streak is available."
    })
    private Message streakRecoveryUnavailable = Message.of("<red>No recoverable streak is available.");
    @Comment({
            "",
            "Admin confirmation after changing shield or recovery currency balances."
    })
    private Message streakCurrencyUpdated = Message.of("<green>Updated <white><player></white>'s <white><currency></white> balance to <white><amount></white>.");
}
