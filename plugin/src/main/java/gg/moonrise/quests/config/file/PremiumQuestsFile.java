package gg.moonrise.quests.config.file;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.Map;
import gg.moonrise.quests.config.ConfigDefaults;
import gg.moonrise.quests.config.menu.PremiumQuestMenu;
import gg.moonrise.quests.config.quest.Rewards;

@Getter
@Configuration
public class PremiumQuestsFile {
    @Comment({
            "",
            "Whether players can receive premium personal quest slots from permissions."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Maps each key to permission questsplus.premium.<key>; the highest matching value is used."
    })
    private Map<String, Integer> permissionLimits = ConfigDefaults.permissionLimits();
    @Comment({
            "",
            "Premium bonus rewards keyed by quest difficulty id. These run after difficulty rewards and before quest-specific rewards."
    })
    private Map<String, Rewards> rewards = ConfigDefaults.premiumRewards();
    @Comment({
            "",
            "Premium quest menu display settings."
    })
    private PremiumQuestMenu menu = new PremiumQuestMenu();

    public PremiumQuestsFile() {
    }

    public PremiumQuestsFile(Map<String, Integer> permissionLimits, PremiumQuestMenu menu) {
        this.permissionLimits = permissionLimits == null ? Map.of() : permissionLimits;
        this.menu = menu == null ? new PremiumQuestMenu() : menu;
    }
}
