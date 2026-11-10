package gg.moonrise.quests.config.menu;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;
import java.util.stream.IntStream;

@Getter
@Configuration
public class MilestoneMenu {
    @Comment({
            "",
            "MiniMessage title for the quest milestone difficulty selector."
    })
    private String selectorTitle = "<dark_gray>Quest Milestones";
    @Comment({
            "",
            "MiniMessage title for a difficulty's milestone page; supports <quest_difficulty>."
    })
    private String milestonesTitle = "<dark_gray><quest_difficulty> Milestones";
    @Comment({
            "",
            "Number of inventory rows in the milestone difficulty selector."
    })
    private int selectorRows = 3;
    @Comment({
            "",
            "Number of inventory rows in a milestone page."
    })
    private int rows = 6;
    @Comment({
            "",
            "Slots used to render difficulties in the milestone selector."
    })
    private List<Integer> selectorSlots = IntStream.range(10, 17).boxed().toList();
    @Comment({
            "",
            "Item rendered in unused milestone menu slots."
    })
    private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
    @Comment({
            "",
            "Item template for a difficulty in the milestone selector."
    })
    private MenuItem difficultyItem = new MenuItem(
            "BOOK",
            "<quest_difficulty>",
            List.of(
                    "<gray>Completed: <white><completed></white>",
                    "<gray>Milestones: <white><milestone_count></white>",
                    "<yellow>Click to view."
            )
    );
    @Comment({
            "",
            "Item template for quest milestones the player has not reached."
    })
    private MilestoneItem lockedMilestone = new MilestoneItem(
            "GRAY_DYE",
            "<gray><milestone_display_name>",
            List.of(
                    "<dark_gray>Locked",
                    "<gray>Progress: <white><completed></white>/<white><milestone_completed></white>"
            )
    );
    @Comment({
            "",
            "Item template for reached quest milestones before automatic claim state is recorded."
    })
    private MilestoneItem unlockedMilestone = new MilestoneItem(
            "LIME_DYE",
            "<green><milestone_display_name>",
            List.of(
                    "<yellow>Unlocked",
                    "<gray>Progress: <white><completed></white>/<white><milestone_completed></white>"
            )
    );
    @Comment({
            "",
            "Item template for quest milestones already claimed and rewarded."
    })
    private MilestoneItem claimedMilestone = new MilestoneItem(
            "EMERALD",
            "<green><milestone_display_name>",
            List.of(
                    "<green>Completed",
                    "<gray>Progress: <white><completed></white>/<white><milestone_completed></white>"
            )
    );
    @Comment({
            "",
            "Slots used to render milestones on each milestone page."
    })
    private List<Integer> milestoneSlots = List.of(18, 19, 28, 37, 38, 39, 30, 21, 22, 23, 32, 41, 42, 43, 34, 25, 26);
    @Comment({
            "",
            "Slot for the previous page button on milestone pages."
    })
    private int previousPageSlot = 45;
    @Comment({
            "",
            "Slot for the next page button on milestone pages."
    })
    private int nextPageSlot = 53;
    @Comment({
            "",
            "Button template for moving to the previous milestone page."
    })
    private MenuItem previousPageButton = new MenuItem("ARROW", "<yellow>Previous Page", List.of("<gray>Go to page <white><previous_page></white>."));
    @Comment({
            "",
            "Button template for moving to the next milestone page."
    })
    private MenuItem nextPageButton = new MenuItem("ARROW", "<yellow>Next Page", List.of("<gray>Go to page <white><next_page></white>."));
    @Comment({
            "",
            "Back button used to return from the milestone difficulty selector to the main quest menu."
    })
    private BackButton selectorBackButton = new BackButton(22, new MenuItem("ARROW", "<yellow>Back", List.of("<gray>Return to quests.")));
    @Comment({
            "",
            "Back button used to return from milestone pages."
    })
    private BackButton backButton = new BackButton();
}
