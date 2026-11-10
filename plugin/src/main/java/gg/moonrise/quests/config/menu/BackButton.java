package gg.moonrise.quests.config.menu;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class BackButton {
    @Comment({
            "",
            "Whether this menu renders a back button."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Slot for the back button."
    })
    private int slot = 49;
    @Comment({
            "",
            "Item template for the back button."
    })
    private MenuItem item = new MenuItem("ARROW", "<yellow>Back", List.of("<gray>Return to difficulties."));

    public BackButton() {
    }

    public BackButton(int slot, MenuItem item) {
        this.slot = slot;
        this.item = item;
    }
}
