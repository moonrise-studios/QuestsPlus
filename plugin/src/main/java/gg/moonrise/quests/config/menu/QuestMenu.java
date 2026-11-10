package gg.moonrise.quests.config.menu;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;
import java.util.stream.IntStream;
import gg.moonrise.quests.config.file.QuestResetsFile;

@Getter
@Configuration
public class QuestMenu {
    @Comment({
            "",
            "MiniMessage title for the daily quest menu."
    })
    private String title = "<dark_gray>Daily Quests";
    @Comment({
            "",
            "Number of inventory rows in the daily quest menu."
    })
    private int rows = 3;
    @Comment({
            "",
            "Slots used for daily quest selection and generated quest items."
    })
    private List<Integer> contentSlots = IntStream.range(10, 17).boxed().toList();
    @Comment({
            "",
            "Item rendered in unused daily quest menu slots."
    })
    private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
    @Comment({
            "",
            "Button that opens the quest completion milestone selector."
    })
    private MilestoneButton milestoneButton = new MilestoneButton();
    @Comment({
            "",
            "Button that opens the quest streak menu."
    })
    private StreakButton streakButton = new StreakButton();
    @Comment({
            "",
            "Button that opens the quest reset purchase menu once every accessible quest slot is complete."
    })
    private QuestResetButton resetButton = new QuestResetButton();
    @Comment({
            "",
            "Purchase choice menu shown before a player resets completed daily quests."
    })
    private QuestResetMenu resetMenu = new QuestResetMenu();
    @Comment({
            "",
            "Item shown for an empty daily quest slot before a player chooses a difficulty."
    })
    private MenuItem emptyQuest = new MenuItem(
            "GRAY_DYE",
            "<yellow>Select Quest Difficulty",
            List.of(
                    "<gray>Slot <white><slot></white> is empty.",
                    "<yellow>Click to pick a difficulty."
            )
    );
    @Comment({
            "",
            "Difficulty picker opened when selecting or rerolling a daily quest slot."
    })
    private DifficultyPicker difficultyPicker = new DifficultyPicker();
    @Comment({
            "",
            "Item template for incomplete generated quests."
    })
    private QuestItem activeQuest = new QuestItem(
            "BOOK",
            "<gold><quest_display_name>",
            List.of(
                    "<quest_description>",
                    "",
                    "<gray>Difficulty: <quest_difficulty>",
                    "<difficulty_lore>",
                    "<premium_lore>",
                    "<gray>Progress: <white><progress></white>/<white><goal_amount></white>",
                    "<gray>Rerolls: <white><rerolls_remaining></white>/<white><rerolls_limit></white>",
                    "<gray><quest_reset_timer>",
                    "<yellow>Click to reroll."
            )
    );
    @Comment({
            "",
            "Item template for completed generated quests."
    })
    private QuestItem completedQuest = new QuestItem(
            "WRITABLE_BOOK",
            "<green><quest_display_name>",
            List.of(
                    "<quest_description>",
                    "",
                    "<gray>Difficulty: <quest_difficulty>",
                    "<difficulty_lore>",
                    "<premium_lore>",
                    "<green>Completed",
                    "<gray>Progress: <white><progress></white>/<white><goal_amount></white>",
                    "<gray><quest_reset_timer>"
            )
    );

    public QuestMenu() {
    }

    public QuestMenu(QuestResetMenu resetMenu) {
        this.resetMenu = resetMenu == null ? new QuestResetMenu() : resetMenu;
    }

    public void applyQuestResets(QuestResetsFile questResets) {
        QuestResetsFile resets = questResets == null ? new QuestResetsFile() : questResets;
        this.resetButton = resets.getButton() == null ? new QuestResetButton() : resets.getButton();
        this.resetMenu = resets.getMenu() == null ? new QuestResetMenu() : resets.getMenu();
    }
}
