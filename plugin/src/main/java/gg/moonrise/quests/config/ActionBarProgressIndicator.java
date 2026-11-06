package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class ActionBarProgressIndicator {
    @Comment({
            "",
            "Whether the ACTION_BAR progress indicator is enabled."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "MiniMessage actionbar title. Supports personal quest placeholders, global quest placeholders, and <percent>."
    })
    private String title = "<gold><quest_display_name> <gray><progress>/<goal_amount> (<percent>)";
}
