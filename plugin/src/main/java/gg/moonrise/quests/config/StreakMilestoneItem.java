package gg.moonrise.quests.config;

import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class StreakMilestoneItem extends MenuItem {

    public StreakMilestoneItem() {
    }

    public StreakMilestoneItem(String material, String name, List<String> lore) {
        super(material, name, lore);
    }
}
