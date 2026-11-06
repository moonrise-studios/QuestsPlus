package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;
import java.util.stream.IntStream;

@Getter
@Configuration
public class DifficultyPicker {
    @Comment({
            "",
            "MiniMessage title for the difficulty picker menu."
    })
    private String title = "<dark_gray>Select Difficulty";
    @Comment({
            "",
            "Number of inventory rows in the difficulty picker menu."
    })
    private int rows = 3;
    @Comment({
            "",
            "Slots used to render available difficulty choices."
    })
    private List<Integer> slots = IntStream.range(10, 17).boxed().toList();
    @Comment({
            "",
            "Item rendered in unused difficulty picker slots."
    })
    private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
    @Comment({
            "",
            "Back button used to return from the difficulty picker to the main quest menu."
    })
    private BackButton backButton = new BackButton(22, new MenuItem("ARROW", "<yellow>Back", List.of("<gray>Return to quests.")));
    @Comment({
            "",
            "Item template for each selectable difficulty."
    })
    private MenuItem difficultyItem = new MenuItem(
            "BOOK",
            "<quest_difficulty>",
            List.of(
                    "<gray>Fill slot <white><slot></white>.",
                    "<gray>Difficulty id: <white><difficulty_id></white>",
                    "<difficulty_lore>",
                    "<yellow>Click to select."
            )
    );
}
