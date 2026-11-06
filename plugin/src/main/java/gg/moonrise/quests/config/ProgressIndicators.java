package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class ProgressIndicators {
    @Comment({
            "",
            "Whether quest progress indicators are enabled."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "BossBar progress indicator settings."
    })
    private BossBarProgressIndicator bossBar = new BossBarProgressIndicator();
    @Comment({
            "",
            "ActionBar progress indicator settings."
    })
    private ActionBarProgressIndicator actionBar = new ActionBarProgressIndicator();
    @Comment({
            "",
            "Chat progress summary indicator settings."
    })
    private ChatProgressIndicator chat = new ChatProgressIndicator();
}
