package gg.moonrise.quests.config.feature;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.Map;
import gg.moonrise.quests.config.ConfigDefaults;

@Getter
@Configuration
public class Rerolls {
    @Comment({
            "",
            "Maps each key to permission questsplus.reroll.<key>; the highest matching value is used."
    })
    private Map<String, Integer> permissionLimits = ConfigDefaults.permissionLimits();
}
