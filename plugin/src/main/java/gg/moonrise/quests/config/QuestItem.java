package gg.moonrise.quests.config;

import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class QuestItem extends MenuItem {

    public QuestItem() {
    }

    public QuestItem(String material, String name, List<String> lore) {
        super(material, name, lore);
    }
}
