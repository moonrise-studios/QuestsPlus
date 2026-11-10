package gg.moonrise.quests.config.indicator;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class BossBarProgressIndicator {
    @Comment({
            "",
            "Whether the BOSS_BAR progress indicator is enabled."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Seconds to keep the bossbar visible after the latest quest progress."
    })
    private int durationSeconds = 10;
    @Comment({
            "",
            "MiniMessage bossbar title. Supports personal quest placeholders, global quest placeholders, and <percent>."
    })
    private String title = "<gold><quest_display_name> <gray><progress>/<goal_amount> (<percent>)";
    @Comment({
            "",
            "Adventure BossBar color. Invalid values fall back to YELLOW."
    })
    private String color = "YELLOW";
    @Comment({
            "",
            "Adventure BossBar overlay. Invalid values fall back to PROGRESS."
    })
    private String overlay = "PROGRESS";
}
