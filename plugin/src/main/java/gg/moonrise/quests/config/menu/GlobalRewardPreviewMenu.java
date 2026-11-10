package gg.moonrise.quests.config.menu;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;
import gg.moonrise.quests.config.ConfigDefaults;

@Getter
@Configuration
public class GlobalRewardPreviewMenu {
    @Comment({
            "",
            "MiniMessage title for the global reward preview."
    })
    private String title = "<dark_gray>Global Quest Rewards";
    @Comment({
            "",
            "Number of inventory rows in the global reward preview."
    })
    private int rows = 6;
    @Comment({
            "",
            "Slots used for full reward percentile tiers."
    })
    private List<Integer> fullRewardSlots = List.of(10, 11, 12, 13);
    @Comment({
            "",
            "Slots used for reduced reward percentile tiers."
    })
    private List<Integer> reducedRewardSlots = List.of(28, 29, 30, 31);
    @Comment({
            "",
            "Item template for full reward tiers. Supports <reward_display_name> and <reward_percentile>."
    })
    private MenuItem fullRewardItem = ConfigDefaults.fullGlobalRewardItem();
    @Comment({
            "",
            "Item template for reduced reward tiers. Supports <reward_display_name> and <reward_percentile>."
    })
    private MenuItem reducedRewardItem = ConfigDefaults.reducedGlobalRewardItem();
    @Comment({
            "",
            "Item rendered in unused reward preview slots."
    })
    private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
    @Comment({
            "",
            "Back button returning to the daily quest menu."
    })
    private BackButton backButton = new BackButton(49, new MenuItem("ARROW", "<yellow>Back", List.of("<gray>Return to quests.")));
}
