package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class MenuButton {
    @Comment({
            "",
            "Whether this button is shown."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Inventory slot for this button."
    })
    private int slot;
    @Comment({
            "",
            "Item template for this button."
    })
    private MenuItem item;

    public MenuButton() {
    }

    public MenuButton(int slot, MenuItem item) {
        this.slot = slot;
        this.item = item;
    }
}
