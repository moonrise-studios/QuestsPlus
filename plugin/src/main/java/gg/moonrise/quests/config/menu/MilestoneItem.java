package gg.moonrise.quests.config.menu;

import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class MilestoneItem extends MenuItem {

    public MilestoneItem() {
    }

    public MilestoneItem(String material, String name, List<String> lore) {
        super(material, name, lore);
    }
}
