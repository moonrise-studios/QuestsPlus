package gg.moonrise.quests.config.menu;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import gg.moonrise.quests.config.ConfigDefaults;

@Getter
@Configuration
public class PremiumQuestMenu {
    @Comment({
            "",
            "Optional Paper tooltip style key applied only to premium generated quest items. Leave blank to avoid setting one."
    })
    private String tooltipStyle = "minecraft:premium_quest";
    @Comment({
            "",
            "MiniMessage suffix appended to premium generated quest item display names. Leave blank to disable."
    })
    private String displayNameSuffix = " <gold><b>PREMIUM QUEST";
    @Comment({
            "",
            "MiniMessage lore lines for premium generated quest items, keyed by quest difficulty id.",
            "Use <premium_lore> in active-quest or completed-quest lore to place these lines."
    })
    private Map<String, List<String>> lore = ConfigDefaults.premiumLore();
    @Comment({
            "",
            "Item shown for visible premium quest slots the player has not unlocked.",
            "Supports <slot>, <slot_index>, <premium_slot>, <premium_slot_index>, and <rank>."
    })
    private MenuItem lockedQuest = new MenuItem(
            "BARRIER",
            "<gold>Premium Quest <slot>",
            List.of(
                    "<gray>Requires <rank></gray>",
                    "<yellow>Purchase <rank> to unlock this quest slot."
            )
    );
    @Comment({
            "",
            "Rank text used by locked premium quest slots, keyed by the one-based displayed quest slot number."
    })
    private Map<String, String> lockedRanks = ConfigDefaults.premiumLockedRanks();

    public PremiumQuestMenu() {
    }

    public PremiumQuestMenu(Map<String, String> lockedRanks) {
        this.lockedRanks = lockedRanks == null ? Map.of() : lockedRanks;
    }
}
