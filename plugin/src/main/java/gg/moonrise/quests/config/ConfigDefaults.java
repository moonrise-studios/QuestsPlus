package gg.moonrise.quests.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import gg.moonrise.quests.config.menu.MenuItem;
import gg.moonrise.quests.config.quest.MilestoneConfig;
import gg.moonrise.quests.config.quest.QuestDefinitionConfig;
import gg.moonrise.quests.config.quest.QuestDifficultyConfig;
import gg.moonrise.quests.config.quest.Rewards;

/**
 * Default values shared by the config types. Every call returns a new instance so each config owns its copy.
 */
public final class ConfigDefaults {

    private ConfigDefaults() {
    }

    public static Map<String, QuestDifficultyConfig> difficulties() {
        Map<String, QuestDifficultyConfig> difficulties = new LinkedHashMap<>();
        QuestDifficultyConfig easy = new QuestDifficultyConfig("<green><b>EASY", List.of(), -1, -1, Map.of(), new Rewards(List.of("eco give <player> 250")), List.of(
                new MilestoneConfig(5, "<green>Easy I", List.of("<gray>Complete easy quests to unlock this reward."), List.of("eco give <player> 1000")),
                new MilestoneConfig(10, "<green>Easy II", List.of("<gray>Keep completing easy quests for larger rewards."), List.of("eco give <player> 2500"))
        ));
        difficulties.put("easy", easy);
        return difficulties;
    }

    public static List<MilestoneConfig> easyMilestones() {
        return List.of(
                new MilestoneConfig(5, "<green>Easy I", List.of("<gray>Complete easy quests to unlock this reward."), List.of("eco give <player> 1000")),
                new MilestoneConfig(10, "<green>Easy II", List.of("<gray>Keep completing easy quests for larger rewards."), List.of("eco give <player> 2500"))
        );
    }

    public static List<QuestDefinitionConfig> globalQuestDefinitions() {
        return List.of(
                QuestDefinitionConfig.defaultKillAllMobs().asGlobalQuest("global-kill-all-mobs", "KILL_ALL_MOBS", "<green>Global Mob Slayer", List.of("2500", "5000", "10000")),
                QuestDefinitionConfig.defaultBreakAllBlocks().asGlobalQuest("global-break-all-blocks", "BREAK_ALL_BLOCKS", "<green>Global Excavator", List.of("25000", "50000", "100000")),
                QuestDefinitionConfig.defaultPlaceAnyBlock().asGlobalQuest("global-place-any-block", "PLACE_ANY_BLOCK", "<green>Global Construction", List.of("25000", "50000", "100000")),
                QuestDefinitionConfig.defaultSmeltAnyItem().asGlobalQuest("global-smelt-anything", "SMELT_ANY_ITEM", "<green>Global Furnace Worker", List.of("2500", "5000", "10000")),
                QuestDefinitionConfig.defaultCraftAnyItem().asGlobalQuest("global-craft-anything", "CRAFT_ANY_ITEM", "<green>Global Crafter", List.of("2500", "5000", "10000")),
                QuestDefinitionConfig.defaultFish().asGlobalQuest("global-angler", "FISH", "<green>Global Angler", List.of("500", "1000", "2500")),
                QuestDefinitionConfig.defaultTravelDistance().asGlobalQuest("global-traveler", "TRAVEL_DISTANCE", "<green>Global Traveler", List.of("250000", "500000", "1000000"))
        );
    }

    public static MenuItem fullGlobalRewardItem() {
        return new MenuItem(
                "EMERALD",
                "<green>Full Reward: <reward_display_name>",
                List.of(
                        "<gray>Quest completion: <white>100%</white>",
                        "<gray>Contributor tier: <white>Top <reward_percentile>%</white>"
                )
        );
    }

    public static MenuItem reducedGlobalRewardItem() {
        return new MenuItem(
                "GOLD_INGOT",
                "<yellow>Reduced Reward: <reward_display_name>",
                List.of(
                        "<gray>Quest completion: <white>50% - 99.9%</white>",
                        "<gray>Contributor tier: <white>Top <reward_percentile>%</white>"
                )
        );
    }

    /**
     * Default rank-to-limit map shared by daily rerolls and premium quest slots. Returns a new
     * mutable map on every call so each config instance owns its own copy.
     */
    public static Map<String, Integer> permissionLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("vip", 1);
        limits.put("vipplus", 2);
        limits.put("mvp", 3);
        return limits;
    }

    public static Map<String, Rewards> premiumRewards() {
        Map<String, Rewards> rewards = new LinkedHashMap<>();
        rewards.put("easy", new Rewards(List.of("eco give <player> 125")));
        return rewards;
    }

    public static Map<String, List<String>> premiumLore() {
        Map<String, List<String>> lore = new LinkedHashMap<>();
        lore.put("easy", List.of(
                "<gold>Premium quest",
                "<gray>Includes bonus premium rewards."
        ));
        return lore;
    }

    public static Map<String, String> premiumLockedRanks() {
        Map<String, String> ranks = new LinkedHashMap<>();
        ranks.put("4", "<green>VIP");
        ranks.put("5", "<aqua>VIP+");
        ranks.put("6", "<gold>MVP");
        return ranks;
    }
}
