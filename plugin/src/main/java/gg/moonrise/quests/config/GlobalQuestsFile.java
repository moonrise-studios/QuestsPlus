package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class GlobalQuestsFile {
    @Comment({
            "",
            "Weekly global quest schedule. Default starts Friday at 05:00 and ends the following Friday at 05:00."
    })
    private GlobalSchedule schedule = new GlobalSchedule();
    @Comment({
            "",
            "Global quest menu item settings. Slot 15 is reserved in the daily quest menu."
    })
    private GlobalQuestMenu menu = new GlobalQuestMenu();
    @Comment({
            "",
            "Data-driven global quest definitions. These are separate from personal quests.yml definitions.",
            "Built-in quest types: KILL_MOB, KILL_MOB_IN_WORLD, BLOCK_BREAK, ENCHANT_ITEM, PLACE_BLOCK, PLACE_ANY_BLOCK, HARVEST_ITEM, SHEAR_SHEEP, FISH, EAT_CAKE_SLICE, CRAFT_ITEM, CRAFT_ANY_ITEM, DYE_SHEEP, KILL_ALL_MOBS, BREAK_ALL_BLOCKS, SMELT_ITEM, SMELT_ANY_ITEM, BREW_ITEM, BREW_ANY_ITEM, TRAVEL_DISTANCE, MILK_MOB, THROW_ITEM, VILLAGER_TRADE.",
            "External plugins may register additional data-driven quest type keys through sdk."
    })
    private List<QuestDefinitionConfig> questDefinitions = ConfigDefaults.globalQuestDefinitions();
    @Comment({
            "",
            "Full stacked percentile reward tiers evaluated when the global quest reaches 100% completion."
    })
    private List<GlobalRewardTierConfig> rewardTiers = List.of(
            new GlobalRewardTierConfig(1, "<gold>Top 1%", ConfigDefaults.fullGlobalRewardItem(), List.of("eco give <player> 10000")),
            new GlobalRewardTierConfig(10, "<yellow>Top 10%", ConfigDefaults.fullGlobalRewardItem(), List.of("eco give <player> 5000")),
            new GlobalRewardTierConfig(25, "<green>Top 25%", ConfigDefaults.fullGlobalRewardItem(), List.of("eco give <player> 2500")),
            new GlobalRewardTierConfig(100, "<gray>Participation", ConfigDefaults.fullGlobalRewardItem(), List.of("eco give <player> 500"))
    );
    @Comment({
            "",
            "Minimum global quest completion percent required to run reduced reward tiers.",
            "Values below 0 are treated as 0; values above 100 are treated as 100."
    })
    private double reducedRewardMinimumPercent = 50.0D;
    @Comment({
            "",
            "Reduced stacked percentile reward tiers evaluated when the global quest ends below 100% but at or above reduced-reward-minimum-percent.",
            "No global rewards run below reduced-reward-minimum-percent completion."
    })
    private List<GlobalRewardTierConfig> reducedRewardTiers = List.of(
            new GlobalRewardTierConfig(1, "<gold>Reduced Top 1%", ConfigDefaults.reducedGlobalRewardItem(), List.of("eco give <player> 5000")),
            new GlobalRewardTierConfig(10, "<yellow>Reduced Top 10%", ConfigDefaults.reducedGlobalRewardItem(), List.of("eco give <player> 2500")),
            new GlobalRewardTierConfig(25, "<green>Reduced Top 25%", ConfigDefaults.reducedGlobalRewardItem(), List.of("eco give <player> 1250")),
            new GlobalRewardTierConfig(100, "<gray>Reduced Participation", ConfigDefaults.reducedGlobalRewardItem(), List.of("eco give <player> 250"))
    );
    @Comment({
            "",
            "Messages used by global quest progress, command progress, and period rollover."
    })
    private GlobalQuestMessages messages = new GlobalQuestMessages();

    public GlobalQuestsFile() {
    }

    public GlobalQuestsFile(
            List<GlobalRewardTierConfig> rewardTiers,
            double reducedRewardMinimumPercent,
            List<GlobalRewardTierConfig> reducedRewardTiers
    ) {
        this.rewardTiers = rewardTiers == null ? List.of() : rewardTiers;
        this.reducedRewardMinimumPercent = reducedRewardMinimumPercent;
        this.reducedRewardTiers = reducedRewardTiers == null ? List.of() : reducedRewardTiers;
    }
}
