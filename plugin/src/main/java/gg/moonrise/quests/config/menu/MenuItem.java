package gg.moonrise.quests.config.menu;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class MenuItem {
    @Comment({
            "",
            "Bukkit material name used for this menu item."
    })
    private String material;
    @Comment({
            "",
            "MiniMessage display name for this menu item."
    })
    private String name;
    @Comment({
            "",
            "MiniMessage lore lines for this menu item."
    })
    private List<String> lore;

    public MenuItem() {
    }

    public MenuItem(String material, String name, List<String> lore) {
        this.material = material;
        this.name = name;
        this.lore = lore;
    }

    /**
     * Lore lines for this item; a missing or null {@code lore} entry is treated as no lore.
     */
    public List<String> getLore() {
        return lore == null ? List.of() : lore;
    }
}
