package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class ChatProgressIndicator {
    @Comment({
            "",
            "Whether the CHAT progress summary indicator is enabled."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Seconds to batch accepted quest progress before sending one summary message."
    })
    private int intervalSeconds = 30;
    @Comment({
            "",
            "MiniMessage header sent before the batched quest progress lines. Blank disables the header."
    })
    private String header = "<gold>Quest Progress";
    @Comment({
            "",
            "MiniMessage line for each quest progressed during the batch. Supports normal indicator placeholders plus <previous_progress> and <percent>."
    })
    private String line = "<gray>- <yellow><quest_display_name></yellow>: <white><previous_progress></white> <gray>➜</gray> <white><progress></white> <gray>/</gray> <white><goal_amount></white> <gray>(<percent>)</gray>";
}
