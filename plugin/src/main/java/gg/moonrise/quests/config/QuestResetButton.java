package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class QuestResetButton {
    @Comment({
            "",
            "Whether the daily quest menu shows the completed-quest reset button."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Slot for the completed-quest reset button in the daily quest menu."
    })
    private int slot = 18;
    @Comment({
            "",
            "Item template for the completed-quest reset button.",
            "Supports <completed>, <required>, <status>, <resets_used>, <resets_limit>, and <resets_remaining>.",
            "<status> is controlled by quest-resets.yml menu status outputs."
    })
    private MenuItem item = new MenuItem(
            "AMETHYST_SHARD",
            "<gold>Reset Quests",
            List.of(
                    "<gray>Completed: <white><completed></white>/<white><required></white>",
                    "<gray>Resets left: <white><resets_remaining></white>/<white><resets_limit></white>",
                    "<gray>Status: <white><status></white>",
                    "<yellow>Click to purchase a quest reset."
            )
    );
}
