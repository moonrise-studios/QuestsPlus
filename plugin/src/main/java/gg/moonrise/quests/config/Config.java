package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Ignore;
import gg.moonrise.engine.message.Message;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Getter
@Configuration
public class Config {

    @Comment({
            "",
            "Daily quest generation and reset settings."
    })
    private Daily daily = new Daily();

    @Comment({
            "",
            "Local file names used by the plugin."
    })
    private Storage storage = new Storage();

    @Comment({
            "",
            "Daily quest GUI layout."
    })
    private QuestMenu menu = new QuestMenu();

    @Comment({
            "",
            "Quest streak settings, milestones, and menus."
    })
    private Streaks streaks = new Streaks();

    @Comment({
            "",
            "Player and admin-facing messages."
    })
    private Messages messages = new Messages();

    @Comment({
            "",
            "Quest difficulty tags and their baseline completion rewards."
    })
    private Map<String, QuestDifficultyConfig> questDifficulties = defaultDifficulties();

    @Comment({
            "",
            "Data-driven quest definitions. Each id must be unique.",
            "Built-in quest types: KILL_MOB, KILL_MOB_IN_WORLD, BLOCK_BREAK, ENCHANT_ITEM, PLACE_BLOCK, PLACE_ANY_BLOCK, HARVEST_ITEM, SHEAR_SHEEP, FISH, EAT_CAKE_SLICE, CRAFT_ITEM, CRAFT_ANY_ITEM, DYE_SHEEP, KILL_ALL_MOBS, BREAK_ALL_BLOCKS, SMELT_ITEM, SMELT_ANY_ITEM, BREW_ITEM, BREW_ANY_ITEM, TRAVEL_DISTANCE, MILK_MOB, THROW_ITEM, VILLAGER_TRADE.",
            "External plugins may register additional data-driven quest type keys through sdk."
    })
    private List<QuestDefinitionConfig> questDefinitions = List.of(
            QuestDefinitionConfig.defaultKillMob(),
            QuestDefinitionConfig.defaultKillMobInWorld(),
            QuestDefinitionConfig.defaultBlockBreak(),
            QuestDefinitionConfig.defaultEnchantItem(),
            QuestDefinitionConfig.defaultPlaceBlock(),
            QuestDefinitionConfig.defaultPlaceAnyBlock(),
            QuestDefinitionConfig.defaultHarvestItem(),
            QuestDefinitionConfig.defaultShearSheep(),
            QuestDefinitionConfig.defaultFish(),
            QuestDefinitionConfig.defaultEatCakeSlice(),
            QuestDefinitionConfig.defaultCraftItem(),
            QuestDefinitionConfig.defaultCraftAnyItem(),
            QuestDefinitionConfig.defaultDyeSheep(),
            QuestDefinitionConfig.defaultKillAllMobs(),
            QuestDefinitionConfig.defaultBreakAllBlocks(),
            QuestDefinitionConfig.defaultSmeltItem(),
            QuestDefinitionConfig.defaultSmeltAnyItem(),
            QuestDefinitionConfig.defaultBrewItem(),
            QuestDefinitionConfig.defaultBrewAnyItem(),
            QuestDefinitionConfig.defaultTravelDistance(),
            QuestDefinitionConfig.defaultMilkMob(),
            QuestDefinitionConfig.defaultThrowItem(),
            QuestDefinitionConfig.defaultVillagerTrade()
    );

    @Comment({
            "",
            "Quest milestone GUI layout."
    })
    private MilestoneMenu milestoneMenu = new MilestoneMenu();

    @Comment({
            "",
            "Weekly server-wide global quest settings, definitions, menu item, and rewards."
    })
    private GlobalQuestsFile globalQuests = new GlobalQuestsFile();
    @Comment({
            "",
            "Premium personal quest slot settings and bonus rewards."
    })
    private PremiumQuestsFile premiumQuests = new PremiumQuestsFile();

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile
    ) {
        Config config = new Config();
        DailyFile daily = dailyFile == null ? new DailyFile() : dailyFile;
        DifficultyComposition difficulties = composeDifficultyDirectories(difficultyDirectories);
        StreaksFile streaks = streaksFile == null ? new StreaksFile() : streaksFile;
        SharedMessagesFile sharedMessages = sharedMessagesFile == null ? new SharedMessagesFile() : sharedMessagesFile;
        GlobalQuestsFile globalQuests = globalQuestsFile == null ? new GlobalQuestsFile() : globalQuestsFile;
        PremiumQuestsFile premiumQuests = premiumQuestsFile == null ? new PremiumQuestsFile() : premiumQuestsFile;

        config.storage = storage == null ? new Storage() : storage;
        config.daily = daily.toDaily();
        config.menu = daily.menu == null ? new QuestMenu() : daily.menu;
        config.questDefinitions = difficulties.questDefinitions();
        config.questDifficulties = difficulties.questDifficulties();
        config.milestoneMenu = sharedMessages.milestoneMenu == null ? new MilestoneMenu() : sharedMessages.milestoneMenu;
        config.streaks = streaks.toStreaks();
        config.messages = composeMessages(sharedMessages, daily.messages, sharedMessages.milestoneMessages, streaks.messages);
        config.globalQuests = globalQuests;
        config.premiumQuests = premiumQuests;
        return config;
    }

    public record DifficultyDirectory(
            String difficultyId,
            DifficultyConfig settings,
            QuestsFile quests,
            DifficultyMilestonesFile milestones
    ) {
    }

    private record DifficultyComposition(
            Map<String, QuestDifficultyConfig> questDifficulties,
            List<QuestDefinitionConfig> questDefinitions
    ) {
    }

    @Getter
    @Configuration
    public static class Daily {
        @Comment({
                "",
                "Server-local daily reset time in HH:mm format."
        })
        private String resetTime = "05:00";
        @Comment({
                "",
                "When true, personal quest windows reset weekly instead of daily."
        })
        private boolean weekly = false;
        @Comment({
                "",
                "Weekly personal quest reset schedule used only when weekly is true."
        })
        private GlobalSchedule schedule = new GlobalSchedule();
        @Comment({
                "",
                "Number of daily quest slots shown in the quest menu."
        })
        private int questCount = 3;
        @Comment({
                "",
                "Permission-based daily reroll limits."
        })
        private Rerolls rerolls = new Rerolls();
    }

    @Getter
    @Configuration
    public static class Rerolls {
        @Comment({
                "",
                "Maps each key to permission questsplus.reroll.<key>; the highest matching value is used."
        })
        private Map<String, Integer> permissionLimits = defaultRerollLimits();
    }

    @Getter
    @Configuration
    public static class Storage {
        @Comment({
                "",
                "SQLite database file name stored inside the QuestsPlus plugin folder."
        })
        private String databaseFile = "storage/quests.db";
    }

    @Getter
    @Configuration
    public static class DailyFile {
        @Comment({
                "",
                "Server-local daily reset time in HH:mm format."
        })
        private String resetTime = "05:00";
        @Comment({
                "",
                "When true, personal quest windows reset weekly instead of daily."
        })
        private boolean weekly = false;
        @Comment({
                "",
                "Weekly personal quest reset schedule used only when weekly is true."
        })
        private GlobalSchedule schedule = new GlobalSchedule();
        @Comment({
                "",
                "Number of daily quest slots shown in the quest menu."
        })
        private int questCount = 3;
        @Comment({
                "",
                "Permission-based daily reroll limits."
        })
        private Rerolls rerolls = new Rerolls();
        @Comment({
                "",
                "Daily quest menu, empty-slot item, quest item, and difficulty picker layout."
        })
        private QuestMenu menu = new QuestMenu();
        @Comment({
                "",
                "Daily quest, progress, reset, and reroll messages."
        })
        private DailyMessages messages = new DailyMessages();

        private Daily toDaily() {
            Daily daily = new Daily();
            daily.resetTime = resetTime;
            daily.weekly = weekly;
            daily.schedule = schedule == null ? new GlobalSchedule() : schedule;
            daily.questCount = questCount;
            daily.rerolls = rerolls == null ? new Rerolls() : rerolls;
            return daily;
        }
    }

    @Getter
    @Configuration
    public static class QuestsFile {
        @Comment({
                "",
                "Data-driven quest definitions. Each id must be unique.",
                "Built-in quest types: KILL_MOB, KILL_MOB_IN_WORLD, BLOCK_BREAK, ENCHANT_ITEM, PLACE_BLOCK, PLACE_ANY_BLOCK, HARVEST_ITEM, SHEAR_SHEEP, FISH, EAT_CAKE_SLICE, CRAFT_ITEM, CRAFT_ANY_ITEM, DYE_SHEEP, KILL_ALL_MOBS, BREAK_ALL_BLOCKS, SMELT_ITEM, SMELT_ANY_ITEM, BREW_ITEM, BREW_ANY_ITEM, TRAVEL_DISTANCE, MILK_MOB, THROW_ITEM, VILLAGER_TRADE.",
                "External plugins may register additional data-driven quest type keys through sdk."
        })
        private List<QuestDefinitionConfig> questDefinitions = List.of(
                QuestDefinitionConfig.defaultKillMob(),
                QuestDefinitionConfig.defaultKillMobInWorld(),
                QuestDefinitionConfig.defaultBlockBreak(),
                QuestDefinitionConfig.defaultEnchantItem(),
                QuestDefinitionConfig.defaultPlaceBlock(),
                QuestDefinitionConfig.defaultPlaceAnyBlock(),
                QuestDefinitionConfig.defaultHarvestItem(),
                QuestDefinitionConfig.defaultShearSheep(),
                QuestDefinitionConfig.defaultFish(),
                QuestDefinitionConfig.defaultEatCakeSlice(),
                QuestDefinitionConfig.defaultCraftItem(),
                QuestDefinitionConfig.defaultCraftAnyItem(),
                QuestDefinitionConfig.defaultDyeSheep(),
                QuestDefinitionConfig.defaultKillAllMobs(),
                QuestDefinitionConfig.defaultBreakAllBlocks(),
                QuestDefinitionConfig.defaultSmeltItem(),
                QuestDefinitionConfig.defaultSmeltAnyItem(),
                QuestDefinitionConfig.defaultBrewItem(),
                QuestDefinitionConfig.defaultBrewAnyItem(),
                QuestDefinitionConfig.defaultTravelDistance(),
                QuestDefinitionConfig.defaultMilkMob(),
                QuestDefinitionConfig.defaultThrowItem(),
                QuestDefinitionConfig.defaultVillagerTrade()
        );
    }

    @Getter
    @Configuration
    public static class DifficultyConfig {
        @Comment({
                "",
                "MiniMessage display name shown for this difficulty."
        })
        private String displayName = "<green><b>EASY";
        @Comment({
                "",
                "MiniMessage lore lines inserted by <difficulty_lore> in quest menu items."
        })
        private List<String> lore = List.of(
                "<gray>Possible reward: <green>$250"
        );
        @Comment({
                "",
                "Inventory slot for this difficulty in the daily difficulty picker; -1 uses the menu slot order."
        })
        private int pickerSlot = -1;
        @Comment({
                "",
                "Inventory slot for this difficulty in the milestone difficulty selector; -1 uses the menu slot order."
        })
        private int milestonesSlot = -1;
        @Comment({
                "",
                "Completed quest requirements needed before players can select this difficulty.",
                "Keys are difficulty ids and values are the number of completed quests needed for that difficulty."
        })
        private Map<String, Integer> requirements = Map.of();
        @Comment({
                "",
                "Random baseline reward command pool; one command is selected when a quest of this difficulty is completed."
        })
        private Rewards rewards = new Rewards(List.of("eco give <player> 250"));

        public DifficultyConfig() {
        }

        public DifficultyConfig(String displayName, Rewards rewards) {
            this.displayName = displayName;
            this.rewards = rewards;
        }

        public DifficultyConfig(String displayName, List<String> lore, Rewards rewards) {
            this.displayName = displayName;
            this.lore = lore;
            this.rewards = rewards;
        }

        public DifficultyConfig(String displayName, List<String> lore, int pickerSlot, int milestonesSlot, Rewards rewards) {
            this.displayName = displayName;
            this.lore = lore;
            this.pickerSlot = pickerSlot;
            this.milestonesSlot = milestonesSlot;
            this.rewards = rewards;
        }
    }

    @Getter
    @Configuration
    public static class DifficultyMilestonesFile {
        @Comment({
                "",
                "Quest completion milestones for this difficulty."
        })
        private List<MilestoneConfig> milestones = defaultEasyMilestones();
    }

    @Getter
    @Configuration
    public static class StreaksFile {
        @Comment({
                "",
                "Daily completions required to gain one streak point; -1 means all selected daily quests."
        })
        private int dailyRequiredCompletions = -1;
        @Comment({
                "",
                "Days after a streak loss during which Streak Recovery may restore it."
        })
        private int recoveryWindowDays = 3;
        @Comment({
                "",
                "Automatic streak milestone rewards keyed by required streak length."
        })
        private List<StreakMilestoneConfig> milestones = List.of(
                new StreakMilestoneConfig(3, "<green>3 Day Streak", List.of("<gray>Reward for keeping your streak alive."), List.of("eco give <player> 500")),
                new StreakMilestoneConfig(7, "<gold>7 Day Streak", List.of("<gray>One full week of completed quest windows."), List.of("eco give <player> 1500"))
        );
        @Comment({
                "",
                "Streak status, automatic shield, recovery, milestone, and confirmation menu layout."
        })
        private StreakMenu menu = new StreakMenu();
        @Comment({
                "",
                "Messages used by streak recovery and admin currency actions."
        })
        private StreakMessages messages = new StreakMessages();

        private Streaks toStreaks() {
            Streaks streaks = new Streaks();
            streaks.dailyRequiredCompletions = dailyRequiredCompletions;
            streaks.recoveryWindowDays = recoveryWindowDays;
            streaks.milestones = milestones == null ? List.of() : List.copyOf(milestones);
            streaks.menu = menu == null ? new StreakMenu() : menu;
            return streaks;
        }
    }

    @Getter
    @Configuration
    public static class SharedMessagesFile {
        @Comment({
                "",
                "Shared command and admin messages that are not tied to one feature file."
        })
        private SharedMessages messages = new SharedMessages();
        @Comment({
                "",
                "Messages used for completed quest totals and per-difficulty summaries."
        })
        private MilestoneMessages milestoneMessages = new MilestoneMessages();
        @Comment({
                "",
                "Quest milestone selector and milestone page menu layout."
        })
        private MilestoneMenu milestoneMenu = new MilestoneMenu();
        @Comment({
                "",
                "Progress indicator UI settings used when personal or global quest progress is applied."
        })
        private ProgressIndicators progressIndicators = new ProgressIndicators();
    }

    @Getter
    @Configuration
    public static class GlobalQuestsFile {
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
        private List<QuestDefinitionConfig> questDefinitions = defaultGlobalQuestDefinitions();
        @Comment({
                "",
                "Full stacked percentile reward tiers evaluated when the global quest reaches 100% completion."
        })
        private List<GlobalRewardTierConfig> rewardTiers = List.of(
                new GlobalRewardTierConfig(1, "<gold>Top 1%", defaultFullGlobalRewardItem(), List.of("eco give <player> 10000")),
                new GlobalRewardTierConfig(10, "<yellow>Top 10%", defaultFullGlobalRewardItem(), List.of("eco give <player> 5000")),
                new GlobalRewardTierConfig(25, "<green>Top 25%", defaultFullGlobalRewardItem(), List.of("eco give <player> 2500")),
                new GlobalRewardTierConfig(100, "<gray>Participation", defaultFullGlobalRewardItem(), List.of("eco give <player> 500"))
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
                new GlobalRewardTierConfig(1, "<gold>Reduced Top 1%", defaultReducedGlobalRewardItem(), List.of("eco give <player> 5000")),
                new GlobalRewardTierConfig(10, "<yellow>Reduced Top 10%", defaultReducedGlobalRewardItem(), List.of("eco give <player> 2500")),
                new GlobalRewardTierConfig(25, "<green>Reduced Top 25%", defaultReducedGlobalRewardItem(), List.of("eco give <player> 1250")),
                new GlobalRewardTierConfig(100, "<gray>Reduced Participation", defaultReducedGlobalRewardItem(), List.of("eco give <player> 250"))
        );
        @Comment({
                "",
                "Messages used by global quest progress, command progress, and period rollover."
        })
        private GlobalQuestMessages messages = new GlobalQuestMessages();
    }

    @Getter
    @Configuration
    public static class PremiumQuestsFile {
        @Comment({
                "",
                "Whether players can receive premium personal quest slots from permissions."
        })
        private boolean enabled = true;
        @Comment({
                "",
                "Maps each key to permission questsplus.premium.<key>; the highest matching value is used."
        })
        private Map<String, Integer> permissionLimits = defaultPremiumLimits();
        @Comment({
                "",
                "Premium bonus rewards keyed by quest difficulty id. These run after difficulty rewards and before quest-specific rewards."
        })
        private Map<String, Rewards> rewards = defaultPremiumRewards();
        @Comment({
                "",
                "Premium quest menu display settings."
        })
        private PremiumQuestMenu menu = new PremiumQuestMenu();
    }

    @Getter
    @Configuration
    public static class PremiumQuestMenu {
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
        private Map<String, List<String>> lore = defaultPremiumLore();
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
        private Map<String, String> lockedRanks = defaultPremiumLockedRanks();
    }

    @Getter
    @Configuration
    public static class GlobalSchedule {
        @Comment({
                "",
                "Day of week for this weekly boundary. Use names like FRIDAY."
        })
        private String dayOfWeek = "FRIDAY";
        @Comment({
                "",
                "Server-local boundary time in HH:mm format."
        })
        private String time = "05:00";
    }

    @Getter
    @Configuration
    public static class GlobalQuestMenu {
        @Comment({
                "",
                "Fixed inventory slot for the global quest item in the daily quest menu."
        })
        private int slot = 15;
        @Comment({
                "",
                "Item template shown while the active global quest is incomplete."
        })
        private QuestItem activeQuest = new QuestItem(
                "NETHER_STAR",
                "<gold>Global Quest: <quest_display_name>",
                List.of(
                        "<quest_description>",
                        "",
                        "<gray>Progress: <white><global_progress></white>/<white><global_goal_amount></white> <gray>(<global_percent>%)",
                        "<gray>Your Contribution: <white><contribution></white> <gray>(<contribution_percent>%)",
                        "<gray>Rank: <white>#<global_rank></white>/<white><global_participants></white>",
                        "<gray><global_time_remaining>"
                )
        );
        @Comment({
                "",
                "Item template shown after the active global quest reaches its goal."
        })
        private QuestItem completedQuest = new QuestItem(
                "EMERALD",
                "<green>Global Quest Complete: <quest_display_name>",
                List.of(
                        "<quest_description>",
                        "",
                        "<green>Completed",
                        "<gray>Your Contribution: <white><contribution></white> <gray>(<contribution_percent>%)",
                        "<gray>Rank: <white>#<global_rank></white>/<white><global_participants></white>",
                        "<gray><global_time_remaining>"
                )
        );
        @Comment({
                "",
                "Item shown when no valid global quest definition is available for the current period."
        })
        private MenuItem noActiveQuest = new MenuItem(
                "BARRIER",
                "<red>No Global Quest",
                List.of("<gray>No global quest is active right now.")
        );
        @Comment({
                "",
                "Cosmetic reward preview opened by clicking the global quest item."
        })
        private GlobalRewardPreviewMenu rewardPreview = new GlobalRewardPreviewMenu();
    }

    @Getter
    @Configuration
    public static class GlobalRewardPreviewMenu {
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
        private MenuItem fullRewardItem = defaultFullGlobalRewardItem();
        @Comment({
                "",
                "Item template for reduced reward tiers. Supports <reward_display_name> and <reward_percentile>."
        })
        private MenuItem reducedRewardItem = defaultReducedGlobalRewardItem();
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

    @Getter
    @Configuration
    public static class GlobalRewardTierConfig {
        @Comment({
                "",
                "Percentile threshold for this stacked tier. 1 means top 1%, 10 means top 10%."
        })
        private int percentile = 100;
        @Comment({
                "",
                "MiniMessage display name for this reward tier."
        })
        private String displayName = "<gray>Participation";
        @Comment({
                "",
                "Optional item override for this reward tier in the global reward preview.",
                "Supports <reward_display_name> and <reward_percentile> in name and lore.",
                "When unset, the menu reward-preview full-reward-item or reduced-reward-item template is used."
        })
        private MenuItem item;
        @Comment({
                "",
                "Console commands run for players who qualify for this tier."
        })
        private List<String> commands = List.of();

        public GlobalRewardTierConfig() {
        }

        public GlobalRewardTierConfig(int percentile, String displayName, List<String> commands) {
            this(percentile, displayName, null, commands);
        }

        public GlobalRewardTierConfig(int percentile, String displayName, MenuItem item, List<String> commands) {
            this.percentile = percentile;
            this.displayName = displayName;
            this.item = item;
            this.commands = commands;
        }
    }

    @Getter
    @Configuration
    public static class GlobalQuestMessages {
        @Comment({
                "",
                "Broadcast sent when a new global quest period starts."
        })
        private Message periodStarted = Message.of("<green>Global Quest Started: <white><quest_display_name></white>");
        @Comment({
                "",
                "Broadcast sent when a global quest period ends."
        })
        private Message periodEnded = Message.of("<gold>The global quest period has ended.");
        @Comment({
                "",
                "Message sent when no active global quest can receive progress."
        })
        private Message noActiveQuest = Message.of("<yellow>No active global quest was progressed.");
        @Comment({
                "",
                "Admin confirmation when global quest admin progress is applied."
        })
        private Message commandProgress = Message.of("<green>Added <white><amount></white> progress to the active global <white><quest_type></white> quest for <white><player></white>.");
        @Comment({
                "",
                "Admin message when the active global quest does not match the requested quest type."
        })
        private Message commandNoActive = Message.of("<yellow>No active global <white><quest_type></white> quest was progressed for <white><player></white>.");
        @Comment({
                "",
                "Message sent when an admin provides an invalid global quest type."
        })
        private Message invalidQuestType = Message.of("<red>Invalid global quest type: <white><quest_type></white>.");
    }

    @Getter
    @Configuration
    public static class QuestMenu {
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
    }

    @Getter
    @Configuration
    public static class DifficultyPicker {
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

    @Getter
    @Configuration
    public static class MilestoneButton {
        @Comment({
                "",
                "Whether the daily quest menu shows the milestone button."
        })
        private boolean enabled = true;
        @Comment({
                "",
                "Slot for the milestone button in the daily quest menu."
        })
        private int slot = 22;
        @Comment({
                "",
                "Item template for the milestone button."
        })
        private MenuItem item = new MenuItem(
                "NETHER_STAR",
                "<gold>Quest Milestones",
                List.of("<gray>View quest completion milestones.")
        );
    }

    @Getter
    @Configuration
    public static class StreakButton {
        @Comment({
                "",
                "Whether the daily quest menu shows the streak button."
        })
        private boolean enabled = true;
        @Comment({
                "",
                "Slot for the streak button in the daily quest menu."
        })
        private int slot = 26;
        @Comment({
                "",
                "Item template for the streak button."
        })
        private MenuItem item = new MenuItem(
                "BLAZE_POWDER",
                "<gold>Quest Streaks",
                List.of("<gray>View streaks, shields, and recovery.")
        );
    }

    @Getter
    @Configuration
    public static class QuestResetButton {
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
                "<status> is controlled by menu.reset-menu status outputs."
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

    @Getter
    @Configuration
    public static class QuestResetMenu {
        @Comment({
                "",
                "MiniMessage title for the quest reset purchase choice menu."
        })
        private String title = "<dark_gray>Buy Quest Reset";
        @Comment({
                "",
                "Number of inventory rows in the quest reset purchase choice menu."
        })
        private int rows = 3;
        @Comment({
                "",
                "Item rendered in unused reset purchase menu slots."
        })
        private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
        @Comment({
                "",
                "Player Points cost, backed by PlayerPoints, charged when the player chooses Player Points."
        })
        private int pointsCost = 25;
        @Comment({
                "",
                "Vault economy cost charged when the player chooses Ingame Money."
        })
        private double moneyCost = 1000.0D;
        @Comment({
                "",
                "Maximum quest reset purchases each player may make per active reset window.",
                "Values below 0 are treated as 0."
        })
        private int dailyLimit = 1;
        @Comment({
                "",
                "Status text used for <status> when the player may purchase a Quest Reset."
        })
        private String statusReady = "Ready";
        @Comment({
                "",
                "Status text used for <status> when the player has not completed every accessible quest."
        })
        private String statusIncomplete = "Complete all quests";
        @Comment({
                "",
                "Status text used for <status> when the player has no Quest Reset purchases remaining."
        })
        private String statusLimitReached = "You have already used your resets for the day";
        @Comment({
                "",
                "Button for purchasing with Player Points. Supports <completed>, <required>, <status>, <payment>, <reward>, <amount>, <resets_used>, <resets_limit>, and <resets_remaining>."
        })
        private MenuButton pointsButton = new MenuButton(11, new MenuItem(
                "SUNFLOWER",
                "<gold>Player Points",
                List.of(
                        "<gray>Cost: <white><amount></white> points",
                        "<gray>Resets left: <white><resets_remaining></white>/<white><resets_limit></white>",
                        "<gray>Resets <white><completed></white>/<white><required></white> completed quests.",
                        "<yellow>Click to buy and reset."
                )
        ));
        @Comment({
                "",
                "Button for purchasing with Vault ingame money. Supports <completed>, <required>, <status>, <payment>, <reward>, <amount>, <resets_used>, <resets_limit>, and <resets_remaining>."
        })
        private MenuButton moneyButton = new MenuButton(15, new MenuItem(
                "EMERALD",
                "<green>Ingame Money",
                List.of(
                        "<gray>Cost: <white><amount></white>",
                        "<gray>Resets left: <white><resets_remaining></white>/<white><resets_limit></white>",
                        "<gray>Resets <white><completed></white>/<white><required></white> completed quests.",
                        "<yellow>Click to buy and reset."
                )
        ));
        @Comment({
                "",
                "Back button used to return from the reset purchase choice menu to daily quests."
        })
        private BackButton backButton = new BackButton(22, new MenuItem("ARROW", "<yellow>Back", List.of("<gray>Return to quests.")));
    }

    @Getter
    @Configuration
    public static class Streaks {
        @Comment({
                "",
                "Daily completions required to gain one streak point; -1 means all selected daily quests."
        })
        private int dailyRequiredCompletions = -1;
        @Comment({
                "",
                "Days after a streak loss during which Streak Recovery may restore it."
        })
        private int recoveryWindowDays = 3;
        @Comment({
                "",
                "Automatic streak milestone rewards keyed by required streak length."
        })
        private List<StreakMilestoneConfig> milestones = List.of(
                new StreakMilestoneConfig(3, "<green>3 Day Streak", List.of("<gray>Reward for keeping your streak alive."), List.of("eco give <player> 500")),
                new StreakMilestoneConfig(7, "<gold>7 Day Streak", List.of("<gray>One full week of completed quest windows."), List.of("eco give <player> 1500"))
        );
        @Comment({
                "",
                "Streak status, automatic shield, recovery, milestone, and confirmation menu layout."
        })
        private StreakMenu menu = new StreakMenu();
    }

    @Getter
    @Configuration
    public static class StreakMenu {
        @Comment({
                "",
                "MiniMessage title for the streak menu."
        })
        private String title = "<dark_gray>Quest Streaks";
        @Comment({
                "",
                "Number of inventory rows in the streak menu."
        })
        private int rows = 6;
        @Comment({
                "",
                "Slots used to render streak milestones in order."
        })
        private List<Integer> milestoneSlots = List.of(18, 19, 28, 37, 38, 39, 30, 21, 22, 23, 32, 41, 42, 43, 34, 25, 26);
        @Comment({
                "",
                "Item rendered in unused streak menu slots."
        })
        private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
        @Comment({
                "",
                "Status item showing current streak, highest streak, and daily progress."
        })
        private MenuItem statusItem = new MenuItem(
                "CLOCK",
                "<gold>Current Streak",
                List.of(
                        "<gray>Current: <white><streak></white>",
                        "<gray>Highest: <white><highest_streak></white>",
                        "<gray>Today: <white><daily_completed></white>/<white><daily_required></white>"
                )
        );
        @Comment({
                "",
                "Slot for the streak status item."
        })
        private int statusSlot = 4;
        @Comment({
                "",
                "Informational item showing available Streak Shields. Shields are consumed automatically when a missed reset would break the streak."
        })
        private MenuItem shieldButton = new MenuItem(
                "SHIELD",
                "<aqua>Streak Shields",
                List.of(
                        "<gray>Balance: <white><shield_balance></white>",
                        "<gray>Automatically protects missed quest windows."
                )
        );
        @Comment({
                "",
                "Slot for the Streak Shield button."
        })
        private int shieldSlot = 45;
        @Comment({
                "",
                "Button that opens the Streak Recovery confirmation menu."
        })
        private MenuItem recoveryButton = new MenuItem(
                "TOTEM_OF_UNDYING",
                "<green>Recover Lost Streak",
                List.of(
                        "<gray>Balance: <white><recovery_balance></white>",
                        "<gray>Last Lost: <white><last_lost_streak></white>",
                        "<gray>Recovery Days Left: <white><recovery_days_remaining></white>",
                        "<yellow>Click to recover if available."
                )
        );
        @Comment({
                "",
                "Slot for the Streak Recovery button."
        })
        private int recoverySlot = 53;
        @Comment({
                "",
                "Back button used to return from the streak menu."
        })
        private BackButton backButton = new BackButton();
        @Comment({
                "",
                "Item template for streak milestones the player has not reached."
        })
        private StreakMilestoneItem lockedMilestone = new StreakMilestoneItem(
                "GRAY_DYE",
                "<gray><milestone_display_name>",
                List.of("<streak_locked>", "<streak_progress>")
        );
        @Comment({
                "",
                "Item template for streak milestones already reached and rewarded."
        })
        private StreakMilestoneItem claimedMilestone = new StreakMilestoneItem(
                "EMERALD",
                "<green><milestone_display_name>",
                List.of("<streak_completed>", "<streak_progress>")
        );
        @Comment({
                "",
                "Confirmation menu used before applying recovery."
        })
        private ConfirmationMenu confirmation = new ConfirmationMenu();
    }

    @Getter
    @Configuration
    public static class ConfirmationMenu {
        @Comment({
                "",
                "MiniMessage title for the Streak Recovery confirmation menu."
        })
        private String recoveryTitle = "<dark_gray>Recover Streak?";
        @Comment({
                "",
                "Number of inventory rows in confirmation menus."
        })
        private int rows = 3;
        @Comment({
                "",
                "Item rendered in unused confirmation menu slots."
        })
        private MenuItem filler = new MenuItem("BLACK_STAINED_GLASS_PANE", " ", List.of());
        @Comment({
                "",
                "Slot for the confirmation button."
        })
        private int confirmSlot = 11;
        @Comment({
                "",
                "Item template for confirming recovery use."
        })
        private MenuItem confirmButton = new MenuItem("LIME_CONCRETE", "<green>Confirm", List.of("<gray>This action consumes one currency."));
        @Comment({
                "",
                "Slot for the cancel button."
        })
        private int cancelSlot = 15;
        @Comment({
                "",
                "Item template for cancelling shield or recovery use."
        })
        private MenuItem cancelButton = new MenuItem("RED_CONCRETE", "<red>Cancel", List.of("<gray>Return to streaks."));
    }

    @Getter
    @Configuration
    public static class StreakMilestoneItem extends MenuItem {

        public StreakMilestoneItem() {
        }

        public StreakMilestoneItem(String material, String name, List<String> lore) {
            super(material, name, lore);
        }
    }

    @Getter
    @Configuration
    public static class StreakMilestoneConfig {
        @Comment({
                "",
                "Streak length required to trigger this milestone."
        })
        private int streak;
        @Comment({
                "",
                "MiniMessage display name for this streak milestone."
        })
        private String displayName;
        @Comment({
                "",
                "Extra MiniMessage lore lines appended to this streak milestone's GUI item."
        })
        private List<String> lore = List.of();
        @Comment({
                "",
                "Console commands run once when this streak milestone is reached."
        })
        private List<String> commands;

        public StreakMilestoneConfig() {
        }

        public StreakMilestoneConfig(int streak, String displayName, List<String> commands) {
            this(streak, displayName, List.of(), commands);
        }

        public StreakMilestoneConfig(int streak, String displayName, List<String> lore, List<String> commands) {
            this.streak = streak;
            this.displayName = displayName;
            this.lore = lore;
            this.commands = commands;
        }
    }

    @Getter
    @Configuration
    public static class MilestoneMenu {
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

    @Getter
    @Configuration
    public static class MilestoneItem extends MenuItem {

        public MilestoneItem() {
        }

        public MilestoneItem(String material, String name, List<String> lore) {
            super(material, name, lore);
        }
    }

    @Getter
    @Configuration
    public static class BackButton {
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

    @Getter
    @Configuration
    public static class MenuButton {
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

    @Getter
    @Configuration
    public static class MenuItem {
        @Comment({
                "",
                "Bukkit material name used for this menu item."
        })
        private String material;
        @Comment({
                "",
                "MiniMessage display name for this menu item."
        })
        private String name;
        @Comment({
                "",
                "MiniMessage lore lines for this menu item."
        })
        private List<String> lore;

        public MenuItem() {
        }

        public MenuItem(String material, String name, List<String> lore) {
            this.material = material;
            this.name = name;
            this.lore = lore;
        }
    }

    @Getter
    @Configuration
    public static class QuestItem extends MenuItem {

        public QuestItem() {
        }

        public QuestItem(String material, String name, List<String> lore) {
            super(material, name, lore);
        }
    }

    @Getter
    @Configuration
    public static class Messages {
        @Comment({
                "",
                "Player command usage message."
        })
        private Message usage = Message.of("<yellow>/quests</yellow> <gray>or</gray> <yellow>/q</yellow> <gray>- Open daily quests\n<yellow>/quests progress</yellow> <gray>or</gray> <yellow>/q progress</yellow> <gray>- View progress\n<yellow>/quests completed</yellow> <gray>or</gray> <yellow>/q completed</yellow> <gray>- View completed quest totals\n<yellow>/quests milestones</yellow> <gray>or</gray> <yellow>/q milestones</yellow> <gray>- View completion milestones\n<yellow>/quests streaks</yellow> <gray>or</gray> <yellow>/q streaks</yellow> <gray>- View quest streaks\n<yellow>/quests indicator <main|global|both> <type|off|default></yellow> <gray>or</gray> <yellow>/q indicator <main|global|both> <type|off|default></yellow> <gray>- Set progress indicators</gray>");
        @Comment({
                "",
                "Admin command usage message."
        })
        private Message adminUsage = Message.of("<yellow>/questsadmin reload</yellow> <gray>or</gray> <yellow>/qa reload</yellow>\n<yellow>/questsadmin listtypes</yellow> <gray>or</gray> <yellow>/qa listtypes</yellow>\n<yellow>/questsadmin reset <player></yellow> <gray>or</gray> <yellow>/qa reset <player></yellow>\n<yellow>/questsadmin complete <player></yellow> <gray>or</gray> <yellow>/qa complete <player></yellow>\n<yellow>/questsadmin dailyrerolls reset <player></yellow> <gray>or</gray> <yellow>/qa dailyrerolls reset <player></yellow>\n<yellow>/questsadmin add <amount> <quest-type> <player></yellow> <gray>or</gray> <yellow>/qa add <amount> <quest-type> <player></yellow>\n<yellow>/questsadmin global add <quest-type> <amount> <player></yellow> <gray>or</gray> <yellow>/qa global add <quest-type> <amount> <player></yellow>\n<yellow>/questsadmin global refresh</yellow> <gray>or</gray> <yellow>/qa global refresh</yellow>\n<yellow>/questsadmin shield give <amount> <player></yellow> <gray>or</gray> <yellow>/qa shield give <amount> <player></yellow>\n<yellow>/questsadmin shield take <amount> <player></yellow> <gray>or</gray> <yellow>/qa shield take <amount> <player></yellow>\n<yellow>/questsadmin recovery give <amount> <player></yellow> <gray>or</gray> <yellow>/qa recovery give <amount> <player></yellow>\n<yellow>/questsadmin recovery take <amount> <player></yellow> <gray>or</gray> <yellow>/qa recovery take <amount> <player></yellow>");
        @Comment({
                "",
                "Message sent after a successful /questsadmin reload or /qa reload."
        })
        private Message reloadSuccess = Message.of("<green>QuestsPlus config reloaded.");
        @Comment({
                "",
                "Message sent when /questsadmin reload or /qa reload fails."
        })
        private Message reloadFailed = Message.of("<red>QuestsPlus config reload failed. Check console for details.");
        @Comment({
                "",
                "Message sent when a player-only command is used by console."
        })
        private Message playerOnly = Message.of("<red>Only players can use this command.");
        @Comment({
                "",
                "Message sent when a player selects a quest progress indicator."
        })
        private Message indicatorSelected = Message.of("<green>Quest progress indicator set to <white><indicator></white>.");
        @Comment({
                "",
                "Message sent when a player resets their quest progress indicator to the default BossBar."
        })
        private Message indicatorReset = Message.of("<green>Quest progress indicator reset to <white>BossBar</white>.");
        @Comment({
                "",
                "Message sent when a requested quest progress indicator is unknown or unavailable."
        })
        private Message indicatorUnavailable = Message.of("<red>Unknown or unavailable quest progress indicator: <white><indicator></white>.");
        @Comment({
                "",
                "Message sent when a player disables a quest progress indicator scope."
        })
        private Message indicatorDisabled = Message.of("<green><scope> quest progress indicator disabled.");
        @Comment({
                "",
                "Message sent when a player has no selected daily quests."
        })
        private Message noQuests = Message.of("<red>No daily quests are selected.");
        @Comment({
                "",
                "Message sent when a selected difficulty has no enabled quest definitions."
        })
        private Message noAvailableQuestForDifficulty = Message.of("<red>No quests are available for difficulty <difficulty><red>.");
        @Comment({
                "",
                "Message sent when a player has not completed the configured requirements for a difficulty.",
                "Supports <difficulty>, <difficulty_id>, and <requirements>."
        })
        private Message difficultyRequirementsNotMet = Message.of("<red>You need <requirements> before selecting <difficulty><red>.");
        @Comment({
                "",
                "Message sent when a player tries to reroll without rerolls remaining."
        })
        private Message noRerollsRemaining = Message.of("<red>You do not have any daily quest rerolls remaining.");
        @Comment({
                "",
                "Message sent when a player tries to reroll a completed quest."
        })
        private Message completedQuestCannotReroll = Message.of("<red>Completed quests cannot be rerolled.");
        @Comment({
                "",
                "Message sent after a successful player quest reroll."
        })
        private Message questRerolled = Message.of("<green>Rerolled quest slot <white><slot></white>. Remaining rerolls: <white><rerolls_remaining></white>.");
        @Comment({
                "",
                "Header sent by /quests progress."
        })
        private Message progressHeader = Message.of("<gold><bold>Daily Quests</bold></gold> <gray>- reset key <white><reset_key></white>");
        @Comment({
                "",
                "Line template for each quest in /quests progress."
        })
        private Message progressLine = Message.of("<gray>- <yellow><quest_display_name></yellow>: <white><progress></white>/<white><goal_amount></white> <gray>(<status>)</gray>");
        @Comment({
                "",
                "Total completed quest count message."
        })
        private Message completedCount = Message.of("<green>You have completed <white><quests_completed></white> quests across all difficulties.");
        @Comment({
                "",
                "Per-difficulty completed quest summary line."
        })
        private Message completedDifficultyLine = Message.of("<gray>- <quest_difficulty><gray>: <white><quests_completed></white>");
        @Comment({
                "",
                "Message sent when a quest completion unlocks a completion milestone.",
                "Supports <quest_display_name>, <quest_difficulty>, <difficulty>, <difficulty_id>, <completed>, <milestone_completed>, and <milestone_display_name>."
        })
        private Message milestoneCompleted = Message.of("<green>Milestone complete: <white><milestone_display_name></white> <gray>(<quest_difficulty>, <milestone_completed> quests).");
        @Comment({
                "",
                "Message sent when an existing completion milestone is rewarded retroactively.",
                "Supports <quest_difficulty>, <difficulty>, <difficulty_id>, <completed>, <milestone_completed>, and <milestone_display_name>."
        })
        private Message milestoneClaimed = Message.of("<green>Milestone reward claimed: <white><milestone_display_name></white> <gray>(<quest_difficulty>, <milestone_completed> quests).");
        @Comment({
                "",
                "Message sent to a player when a quest completes."
        })
        private Message questCompleted = Message.of("<green>Quest completed: <white><quest_display_name></white>.");
        @Comment({
                "",
                "Server broadcast sent when the scheduled daily reset fires."
        })
        private Message questsResetBroadcast = Message.of("<green>Daily quests have reset.");
        @Comment({
                "",
                "Admin confirmation after resetting a player's daily quest selections."
        })
        private Message resetPlayer = Message.of("<green>Reset daily quests for <white><player></white>.");
        @Comment({
                "",
                "Admin confirmation after clearing a player's daily quest selections."
        })
        private Message rerollPlayer = Message.of("<green>Rerolled daily quests for <white><player></white>.");
        @Comment({
                "",
                "Admin confirmation after force-completing a player's current accessible daily quests.",
                "Supports <player> and <quests>."
        })
        private Message completeDailyPlayer = Message.of("<green>Completed <white><quests></white> daily quests for <white><player></white>.");
        @Comment({
                "",
                "Message sent when a player has not completed every accessible quest slot for a manual reset.",
                "Supports <completed> and <required>."
        })
        private Message questResetNotReady = Message.of("<red>Complete all available quests before resetting. <gray>(<completed>/<required>)");
        @Comment({
                "",
                "Message sent when a player already has a quest reset in progress."
        })
        private Message questResetProcessing = Message.of("<yellow>Your quest reset is already being processed.");
        @Comment({
                "",
                "Message sent after a player purchases a quest reset and their current quests are cleared.",
                "Supports <payment>, <reward>, <amount>, <completed>, and <required>."
        })
        private Message questResetSuccess = Message.of("<green>Purchased a Quest Reset for <white><amount></white> <white><payment></white>. Your quests were reset.");
        @Comment({
                "",
                "Message sent when the chosen reset purchase integration is unavailable or rejects the payment.",
                "Supports <payment>, <reward>, <amount>, <completed>, and <required>."
        })
        private Message questResetPurchaseUnavailable = Message.of("<red>You do not have enough <payment> for a Quest Reset, or that payment method is unavailable.");
        @Comment({
                "",
                "Message sent when a player has reached the configured reset purchase limit for the active reset window.",
                "Supports <completed>, <required>, <resets_used>, <resets_limit>, and <resets_remaining>."
        })
        private Message questResetLimitReached = Message.of("<red>You have already used your Quest Reset for this reset window.");
        @Comment({
                "",
                "Message sent when a completed-quest reset fails."
        })
        private Message questResetFailed = Message.of("<red>Failed to reset your quests right now.");
        @Comment({
                "",
                "Admin confirmation when quest progress is applied."
        })
        private Message adminProgress = Message.of("<green>Added <white><amount></white> progress to <white><quest_id></white> for <white><player></white>.");
        @Comment({
                "",
                "Admin message when quest progress does not apply to an active quest."
        })
        private Message adminProgressNotActive = Message.of("<yellow>No active quest <white><quest_id></white> was progressed for <white><player></white>.");
        @Comment({
                "",
                "Message sent when an admin command amount is not positive."
        })
        private Message invalidAdminProgressAmount = Message.of("<red>Amount must be a positive integer.");
        @Comment({
                "",
                "Message sent when an admin command quest type is unknown."
        })
        private Message invalidQuestType = Message.of("<red>Invalid quest type: <white><quest_type></white>.");
        @Comment({
                "",
                "Message sent when an online player argument cannot be resolved."
        })
        private Message playerNotFound = Message.of("<red>Player not found: <white><player></white>.");
        @Comment({
                "",
                "Admin confirmation after refreshing the active global quest."
        })
        private Message globalQuestRefreshed = Message.of("<green>Refreshed the active global quest.");
        @Comment({
                "",
                "Admin confirmation after resetting a player's daily reroll usage."
        })
        private Message rerollsReset = Message.of("<green>Reset daily rerolls for <white><player></white>.");
        @Comment({
                "",
                "Configurable streak milestone locked text."
        })
        private String streakMilestoneLocked = "<dark_gray>Locked";
        @Comment({
                "",
                "Configurable streak milestone completed text."
        })
        private String streakMilestoneCompleted = "<green>Completed";
        @Comment({
                "",
                "Configurable streak milestone progress text. Supports <streak> and <milestone_streak>."
        })
        private String streakMilestoneProgress = "<gray>Progress: <white><streak></white>/<white><milestone_streak></white>";
        @Comment({
                "",
                "Message sent after a successful Streak Recovery."
        })
        private Message streakRecoveryApplied = Message.of("<green>Recovered your streak to <white><streak></white>.");
        @Comment({
                "",
                "Message sent when no recoverable streak is available."
        })
        private Message streakRecoveryUnavailable = Message.of("<red>No recoverable streak is available.");
        @Comment({
                "",
                "Admin confirmation after changing shield or recovery currency balances."
        })
        private Message streakCurrencyUpdated = Message.of("<green>Updated <white><player></white>'s <white><currency></white> balance to <white><amount></white>.");
        @Comment({
                "",
                "Progress indicator UI settings used when personal or global quest progress is applied."
        })
        private ProgressIndicators progressIndicators = new ProgressIndicators();
    }

    @Getter
    @Configuration
    public static class SharedMessages {
        @Comment({
                "",
                "Player command usage message."
        })
        private Message usage = Message.of("<yellow>/quests</yellow> <gray>or</gray> <yellow>/q</yellow> <gray>- Open daily quests\n<yellow>/quests progress</yellow> <gray>or</gray> <yellow>/q progress</yellow> <gray>- View progress\n<yellow>/quests completed</yellow> <gray>or</gray> <yellow>/q completed</yellow> <gray>- View completed quest totals\n<yellow>/quests milestones</yellow> <gray>or</gray> <yellow>/q milestones</yellow> <gray>- View completion milestones\n<yellow>/quests streaks</yellow> <gray>or</gray> <yellow>/q streaks</yellow> <gray>- View quest streaks\n<yellow>/quests indicator <main|global|both> <type|off|default></yellow> <gray>or</gray> <yellow>/q indicator <main|global|both> <type|off|default></yellow> <gray>- Set progress indicators</gray>");
        @Comment({
                "",
                "Admin command usage message."
        })
        private Message adminUsage = Message.of("<yellow>/questsadmin reload</yellow> <gray>or</gray> <yellow>/qa reload</yellow>\n<yellow>/questsadmin listtypes</yellow> <gray>or</gray> <yellow>/qa listtypes</yellow>\n<yellow>/questsadmin reset <player></yellow> <gray>or</gray> <yellow>/qa reset <player></yellow>\n<yellow>/questsadmin complete <player></yellow> <gray>or</gray> <yellow>/qa complete <player></yellow>\n<yellow>/questsadmin dailyrerolls reset <player></yellow> <gray>or</gray> <yellow>/qa dailyrerolls reset <player></yellow>\n<yellow>/questsadmin add <amount> <quest-type> <player></yellow> <gray>or</gray> <yellow>/qa add <amount> <quest-type> <player></yellow>\n<yellow>/questsadmin global add <quest-type> <amount> <player></yellow> <gray>or</gray> <yellow>/qa global add <quest-type> <amount> <player></yellow>\n<yellow>/questsadmin global refresh</yellow> <gray>or</gray> <yellow>/qa global refresh</yellow>\n<yellow>/questsadmin shield give <amount> <player></yellow> <gray>or</gray> <yellow>/qa shield give <amount> <player></yellow>\n<yellow>/questsadmin shield take <amount> <player></yellow> <gray>or</gray> <yellow>/qa shield take <amount> <player></yellow>\n<yellow>/questsadmin recovery give <amount> <player></yellow> <gray>or</gray> <yellow>/qa recovery give <amount> <player></yellow>\n<yellow>/questsadmin recovery take <amount> <player></yellow> <gray>or</gray> <yellow>/qa recovery take <amount> <player></yellow>");
        @Comment({
                "",
                "Message sent after a successful /questsadmin reload or /qa reload."
        })
        private Message reloadSuccess = Message.of("<green>QuestsPlus config reloaded.");
        @Comment({
                "",
                "Message sent when /questsadmin reload or /qa reload fails."
        })
        private Message reloadFailed = Message.of("<red>QuestsPlus config reload failed. Check console for details.");
        @Comment({
                "",
                "Message sent when a player-only command is used by console."
        })
        private Message playerOnly = Message.of("<red>Only players can use this command.");
        @Comment({
                "",
                "Message sent when a player selects a quest progress indicator."
        })
        private Message indicatorSelected = Message.of("<green>Quest progress indicator set to <white><indicator></white>.");
        @Comment({
                "",
                "Message sent when a player resets their quest progress indicator to the default BossBar."
        })
        private Message indicatorReset = Message.of("<green>Quest progress indicator reset to <white>BossBar</white>.");
        @Comment({
                "",
                "Message sent when a requested quest progress indicator is unknown or unavailable."
        })
        private Message indicatorUnavailable = Message.of("<red>Unknown or unavailable quest progress indicator: <white><indicator></white>.");
        @Comment({
                "",
                "Message sent when a player disables a quest progress indicator scope."
        })
        private Message indicatorDisabled = Message.of("<green><scope> quest progress indicator disabled.");
        @Comment({
                "",
                "Admin confirmation when quest progress is applied."
        })
        private Message adminProgress = Message.of("<green>Added <white><amount></white> progress to <white><quest_id></white> for <white><player></white>.");
        @Comment({
                "",
                "Admin message when quest progress does not apply to an active quest."
        })
        private Message adminProgressNotActive = Message.of("<yellow>No active quest <white><quest_id></white> was progressed for <white><player></white>.");
        @Comment({
                "",
                "Message sent when an admin command amount is not positive."
        })
        private Message invalidAdminProgressAmount = Message.of("<red>Amount must be a positive integer.");
        @Comment({
                "",
                "Message sent when an admin command quest type is unknown."
        })
        private Message invalidQuestType = Message.of("<red>Invalid quest type: <white><quest_type></white>.");
        @Comment({
                "",
                "Message sent when an online player argument cannot be resolved."
        })
        private Message playerNotFound = Message.of("<red>Player not found: <white><player></white>.");
        @Comment({
                "",
                "Admin confirmation after refreshing the active global quest."
        })
        private Message globalQuestRefreshed = Message.of("<green>Refreshed the active global quest.");
        @Comment({
                "",
                "Admin confirmation after resetting a player's daily reroll usage."
        })
        private Message rerollsReset = Message.of("<green>Reset daily rerolls for <white><player></white>.");
        @Comment({
                "",
                "Configurable streak milestone locked text."
        })
        private String streakMilestoneLocked = "<dark_gray>Locked";
        @Comment({
                "",
                "Configurable streak milestone completed text."
        })
        private String streakMilestoneCompleted = "<green>Completed";
        @Comment({
                "",
                "Configurable streak milestone progress text. Supports <streak> and <milestone_streak>."
        })
        private String streakMilestoneProgress = "<gray>Progress: <white><streak></white>/<white><milestone_streak></white>";
    }

    @Getter
    @Configuration
    public static class ProgressIndicators {
        @Comment({
                "",
                "Whether quest progress indicators are enabled."
        })
        private boolean enabled = true;
        @Comment({
                "",
                "Enabled indicator type keys. Supported values: BOSS_BAR, ACTION_BAR, CHAT."
        })
        private List<String> types = List.of("BOSS_BAR");
        @Comment({
                "",
                "BossBar progress indicator settings."
        })
        private BossBarProgressIndicator bossBar = new BossBarProgressIndicator();
        @Comment({
                "",
                "ActionBar progress indicator settings."
        })
        private ActionBarProgressIndicator actionBar = new ActionBarProgressIndicator();
        @Comment({
                "",
                "Chat progress summary indicator settings."
        })
        private ChatProgressIndicator chat = new ChatProgressIndicator();
    }

    @Getter
    @Configuration
    public static class BossBarProgressIndicator {
        @Comment({
                "",
                "Whether the BOSS_BAR progress indicator is enabled."
        })
        private boolean enabled = true;
        @Comment({
                "",
                "Seconds to keep the bossbar visible after the latest quest progress."
        })
        private int durationSeconds = 10;
        @Comment({
                "",
                "MiniMessage bossbar title. Supports personal quest placeholders, global quest placeholders, and <percent>."
        })
        private String title = "<gold><quest_display_name> <gray><progress>/<goal_amount> (<percent>)";
        @Comment({
                "",
                "Adventure BossBar color. Invalid values fall back to YELLOW."
        })
        private String color = "YELLOW";
        @Comment({
                "",
                "Adventure BossBar overlay. Invalid values fall back to PROGRESS."
        })
        private String overlay = "PROGRESS";
    }

    @Getter
    @Configuration
    public static class ActionBarProgressIndicator {
        @Comment({
                "",
                "Whether the ACTION_BAR progress indicator is enabled."
        })
        private boolean enabled = true;
        @Comment({
                "",
                "MiniMessage actionbar title. Supports personal quest placeholders, global quest placeholders, and <percent>."
        })
        private String title = "<gold><quest_display_name> <gray><progress>/<goal_amount> (<percent>)";
    }

    @Getter
    @Configuration
    public static class ChatProgressIndicator {
        @Comment({
                "",
                "Whether the CHAT progress summary indicator is enabled."
        })
        private boolean enabled = true;
        @Comment({
                "",
                "Seconds to batch accepted quest progress before sending one summary message."
        })
        private int intervalSeconds = 30;
        @Comment({
                "",
                "MiniMessage header sent before the batched quest progress lines. Blank disables the header."
        })
        private String header = "<gold>Quest Progress";
        @Comment({
                "",
                "MiniMessage line for each quest progressed during the batch. Supports normal indicator placeholders plus <previous_progress> and <percent>."
        })
        private String line = "<gray>- <yellow><quest_display_name></yellow>: <white><previous_progress></white> <gray>➜</gray> <white><progress></white> <gray>/</gray> <white><goal_amount></white> <gray>(<percent>)</gray>";
    }

    @Getter
    @Configuration
    public static class DailyMessages {
        @Comment({
                "",
                "Message sent when a player has no selected daily quests."
        })
        private Message noQuests = Message.of("<red>No daily quests are selected.");
        @Comment({
                "",
                "Message sent when a selected difficulty has no enabled quest definitions."
        })
        private Message noAvailableQuestForDifficulty = Message.of("<red>No quests are available for difficulty <difficulty><red>.");
        @Comment({
                "",
                "Message sent when a player has not completed the configured requirements for a difficulty.",
                "Supports <difficulty>, <difficulty_id>, and <requirements>."
        })
        private Message difficultyRequirementsNotMet = Message.of("<red>You need <requirements> before selecting <difficulty><red>.");
        @Comment({
                "",
                "Message sent when a player tries to reroll without rerolls remaining."
        })
        private Message noRerollsRemaining = Message.of("<red>You do not have any daily quest rerolls remaining.");
        @Comment({
                "",
                "Message sent when a player tries to reroll a completed quest."
        })
        private Message completedQuestCannotReroll = Message.of("<red>Completed quests cannot be rerolled.");
        @Comment({
                "",
                "Message sent after a successful player quest reroll."
        })
        private Message questRerolled = Message.of("<green>Rerolled quest slot <white><slot></white>. Remaining rerolls: <white><rerolls_remaining></white>.");
        @Comment({
                "",
                "Header sent by /quests progress."
        })
        private Message progressHeader = Message.of("<gold><bold>Daily Quests</bold></gold> <gray>- reset key <white><reset_key></white>");
        @Comment({
                "",
                "Line template for each quest in /quests progress."
        })
        private Message progressLine = Message.of("<gray>- <yellow><quest_display_name></yellow>: <white><progress></white>/<white><goal_amount></white> <gray>(<status>)</gray>");
        @Comment({
                "",
                "Message sent to a player when a quest completes."
        })
        private Message questCompleted = Message.of("<green>Quest completed: <white><quest_display_name></white>.");
        @Comment({
                "",
                "Server broadcast sent when the scheduled daily reset fires."
        })
        private Message questsResetBroadcast = Message.of("<green>Daily quests have reset.");
        @Comment({
                "",
                "Admin confirmation after resetting a player's daily quest selections."
        })
        private Message resetPlayer = Message.of("<green>Reset daily quests for <white><player></white>.");
        @Comment({
                "",
                "Admin confirmation after clearing a player's daily quest selections."
        })
        private Message rerollPlayer = Message.of("<green>Rerolled daily quests for <white><player></white>.");
        @Comment({
                "",
                "Admin confirmation after force-completing a player's current accessible daily quests.",
                "Supports <player> and <quests>."
        })
        private Message completeDailyPlayer = Message.of("<green>Completed <white><quests></white> daily quests for <white><player></white>.");
        @Comment({
                "",
                "Message sent when a player has not completed every accessible quest slot for a manual reset.",
                "Supports <completed> and <required>."
        })
        private Message questResetNotReady = Message.of("<red>Complete all available quests before resetting. <gray>(<completed>/<required>)");
        @Comment({
                "",
                "Message sent when a player already has a quest reset in progress."
        })
        private Message questResetProcessing = Message.of("<yellow>Your quest reset is already being processed.");
        @Comment({
                "",
                "Message sent after a player purchases a quest reset and their current quests are cleared.",
                "Supports <payment>, <reward>, <amount>, <completed>, and <required>."
        })
        private Message questResetSuccess = Message.of("<green>Purchased a Quest Reset for <white><amount></white> <white><payment></white>. Your quests were reset.");
        @Comment({
                "",
                "Message sent when the chosen reset purchase integration is unavailable or rejects the payment.",
                "Supports <payment>, <reward>, <amount>, <completed>, and <required>."
        })
        private Message questResetPurchaseUnavailable = Message.of("<red>You do not have enough <payment> for a Quest Reset, or that payment method is unavailable.");
        @Comment({
                "",
                "Message sent when a player has reached the configured reset purchase limit for the active reset window.",
                "Supports <completed>, <required>, <resets_used>, <resets_limit>, and <resets_remaining>."
        })
        private Message questResetLimitReached = Message.of("<red>You have already used your Quest Reset for this reset window.");
        @Comment({
                "",
                "Message sent when a completed-quest reset fails."
        })
        private Message questResetFailed = Message.of("<red>Failed to reset your quests right now.");
    }

    @Getter
    @Configuration
    public static class MilestoneMessages {
        @Comment({
                "",
                "Total completed quest count message."
        })
        private Message completedCount = Message.of("<green>You have completed <white><quests_completed></white> quests across all difficulties.");
        @Comment({
                "",
                "Per-difficulty completed quest summary line."
        })
        private Message completedDifficultyLine = Message.of("<gray>- <quest_difficulty><gray>: <white><quests_completed></white>");
        @Comment({
                "",
                "Message sent when a quest completion unlocks a completion milestone.",
                "Supports <quest_display_name>, <quest_difficulty>, <difficulty>, <difficulty_id>, <completed>, <milestone_completed>, and <milestone_display_name>."
        })
        private Message milestoneCompleted = Message.of("<green>Milestone complete: <white><milestone_display_name></white> <gray>(<quest_difficulty>, <milestone_completed> quests).");
        @Comment({
                "",
                "Message sent when an existing completion milestone is rewarded retroactively.",
                "Supports <quest_difficulty>, <difficulty>, <difficulty_id>, <completed>, <milestone_completed>, and <milestone_display_name>."
        })
        private Message milestoneClaimed = Message.of("<green>Milestone reward claimed: <white><milestone_display_name></white> <gray>(<quest_difficulty>, <milestone_completed> quests).");
    }

    @Getter
    @Configuration
    public static class StreakMessages {
        @Comment({
                "",
                "Message sent after a successful Streak Recovery."
        })
        private Message streakRecoveryApplied = Message.of("<green>Recovered your streak to <white><streak></white>.");
        @Comment({
                "",
                "Message sent when no recoverable streak is available."
        })
        private Message streakRecoveryUnavailable = Message.of("<red>No recoverable streak is available.");
        @Comment({
                "",
                "Admin confirmation after changing shield or recovery currency balances."
        })
        private Message streakCurrencyUpdated = Message.of("<green>Updated <white><player></white>'s <white><currency></white> balance to <white><amount></white>.");
    }

    @Getter
    @Configuration
    public static class QuestDefinitionConfig {
        @Comment({
                "",
                "Unique quest definition id used for persistence, admin progress, and duplicate prevention."
        })
        private String id = "hostile-hunter";
        @Comment({
                "",
                "Quest type key handled by a registered GoalHandler, such as KILL_MOB, BLOCK_BREAK, CRAFT_ITEM, or an external sdk type."
        })
        private String type = "KILL_MOB";
        @Comment({
                "",
                "Whether this definition can be selected for new daily quest slots."
        })
        private boolean enabled = true;
        @Comment({
                "",
                "Global quest difficulty id. Personal quests inherit the difficulty/<id>/ folder id at load time."
        })
        @Ignore
        private String difficulty;
        @Comment({
                "",
                "Optional limited-time availability window for this definition.",
                "Use date format dd/MM/yyyy, time format HH:mm, and Java timezone ids like America/Halifax or UTC.",
                "Blank begin or end values are open-ended and do not make the default quest time-limited."
        })
        private QuestScheduleConfig schedule = new QuestScheduleConfig();
        @Comment({
                "",
                "MiniMessage display name shown in menus and messages."
        })
        private String displayName = "<green>Hostile Hunter";
        @Comment({
                "",
                "MiniMessage quest description lines; resolved variables may be used as placeholders."
        })
        private List<String> description = List.of("<gray>Kill <white><goal-amount></white> <white><mob-type></white> mobs.");
        @Comment({
                "",
                "Data-driven variables resolved once when a quest is generated."
        })
        private Map<String, VariableConfig> variables = new LinkedHashMap<>();
        @Comment({
                "",
                "Quest-specific bonus rewards run after difficulty rewards."
        })
        private Rewards rewards = new Rewards();

        public QuestDefinitionConfig() {
        }

        public void setDifficultyId(String difficulty) {
            this.difficulty = difficulty;
        }

        public static QuestDefinitionConfig defaultKillMob() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.variables = new LinkedHashMap<>();
            config.variables.put("mob-type", new VariableConfig("LIST", List.of("ZOMBIE", "SKELETON", "CREEPER")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("25", "50", "75", "100")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultKillMobInWorld() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "dungeon-hunter";
            config.type = "KILL_MOB_IN_WORLD";
            config.displayName = "<green>Dungeon Hunter";
            config.description = List.of("<gray>Kill <white><goal-amount></white> <white><mob-type></white> mobs in <white><world></white>.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("mob-type", new VariableConfig("LIST", List.of("ZOMBIE", "SKELETON", "CREEPER")));
            config.variables.put("world", new VariableConfig("LIST", List.of("world")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("25", "50", "75")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultBlockBreak() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "miner";
            config.type = "BLOCK_BREAK";
            config.displayName = "<green>Miner";
            config.description = List.of("<gray>Break <white><goal-amount></white> <white><block-type></white> blocks.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("block-type", new VariableConfig("LIST", List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:deepslate")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("64", "128", "256")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultEnchantItem() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "enchanter";
            config.type = "ENCHANT_ITEM";
            config.displayName = "<green>Enchanter";
            config.description = List.of("<gray>Enchant <white><goal-amount></white> items at an enchantment table.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("3", "5", "10")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultPlaceBlock() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "builder";
            config.type = "PLACE_BLOCK";
            config.displayName = "<green>Builder";
            config.description = List.of("<gray>Place <white><goal-amount></white> <white><block-type></white> blocks.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("block-type", new VariableConfig("LIST", List.of("minecraft:oak_planks", "minecraft:stone_bricks", "minecraft:glass")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("64", "128", "256")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultPlaceAnyBlock() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "place-any-block";
            config.type = "PLACE_ANY_BLOCK";
            config.displayName = "<green>Construction";
            config.description = List.of("<gray>Place <white><goal-amount></white> blocks.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("128", "256", "512")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultHarvestItem() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "harvester";
            config.type = "HARVEST_ITEM";
            config.displayName = "<green>Harvester";
            config.description = List.of("<gray>Harvest <white><goal-amount></white> <white><item-type></white> from blocks.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("item-type", new VariableConfig("LIST", List.of("minecraft:wheat", "minecraft:carrot", "minecraft:potato")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("64", "128", "256")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultShearSheep() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "shepherd";
            config.type = "SHEAR_SHEEP";
            config.displayName = "<green>Shepherd";
            config.description = List.of("<gray>Shear <white><goal-amount></white> sheep.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("10", "25", "50")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultFish() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "angler";
            config.type = "FISH";
            config.displayName = "<green>Angler";
            config.description = List.of("<gray>Catch <white><goal-amount></white> items while fishing.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("10", "25", "50")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultEatCakeSlice() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "cake-break";
            config.type = "EAT_CAKE_SLICE";
            config.displayName = "<green>Cake Break";
            config.description = List.of("<gray>Eat <white><goal-amount></white> cake slices.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("3", "7", "14")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultCraftItem() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "artisan";
            config.type = "CRAFT_ITEM";
            config.displayName = "<green>Artisan";
            config.description = List.of("<gray>Craft <white><goal-amount></white> <white><item-type></white>.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("item-type", new VariableConfig("LIST", List.of("minecraft:crafting_table", "minecraft:torch", "minecraft:chest")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("16", "32", "64")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultCraftAnyItem() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "craft-anything";
            config.type = "CRAFT_ANY_ITEM";
            config.displayName = "<green>Crafter";
            config.description = List.of("<gray>Craft <white><goal-amount></white> items.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("64", "128", "256")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultDyeSheep() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "wool-dyer";
            config.type = "DYE_SHEEP";
            config.displayName = "<green>Wool Dyer";
            config.description = List.of("<gray>Dye <white><goal-amount></white> sheep <white><color></white>.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("color", new VariableConfig("LIST", List.of("RED", "BLUE", "LIME", "YELLOW")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("5", "10", "20")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultKillAllMobs() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "kill-all-mobs";
            config.type = "KILL_ALL_MOBS";
            config.displayName = "<green>Mob Slayer";
            config.description = List.of("<gray>Kill <white><goal-amount></white> mobs.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("50", "100", "150")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultBreakAllBlocks() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "break-all-blocks";
            config.type = "BREAK_ALL_BLOCKS";
            config.displayName = "<green>Excavator";
            config.description = List.of("<gray>Break <white><goal-amount></white> blocks.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("128", "256", "512")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultSmeltItem() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "smelt-iron";
            config.type = "SMELT_ITEM";
            config.displayName = "<green>Smelter";
            config.description = List.of("<gray>Smelt <white><goal-amount></white> <white><item-type></white>.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("item-type", new VariableConfig("LIST", List.of("minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("32", "64", "128")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultSmeltAnyItem() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "smelt-anything";
            config.type = "SMELT_ANY_ITEM";
            config.displayName = "<green>Furnace Worker";
            config.description = List.of("<gray>Smelt <white><goal-amount></white> items.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("75", "150", "300")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultBrewItem() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "brew-potions";
            config.type = "BREW_ITEM";
            config.displayName = "<green>Alchemist";
            config.description = List.of("<gray>Brew <white><goal-amount></white> <white><item-type></white>.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("item-type", new VariableConfig("LIST", List.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("9", "18", "27")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultBrewAnyItem() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "brew-anything";
            config.type = "BREW_ANY_ITEM";
            config.displayName = "<green>Brewer";
            config.description = List.of("<gray>Brew <white><goal-amount></white> items.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("12", "24", "48")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultTravelDistance() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "traveler";
            config.type = "TRAVEL_DISTANCE";
            config.displayName = "<green>Traveler";
            config.description = List.of("<gray>Travel <white><goal-amount></white> blocks.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("5000", "10000", "15000")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultMilkMob() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "milk-run";
            config.type = "MILK_MOB";
            config.displayName = "<green>Milk Run";
            config.description = List.of("<gray>Milk <white><goal-amount></white> <white><mob-type></white>.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("mob-type", new VariableConfig("LIST", List.of("COW", "GOAT", "MOOSHROOM")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("5", "10", "20")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultThrowItem() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "throwing-practice";
            config.type = "THROW_ITEM";
            config.displayName = "<green>Throwing Practice";
            config.description = List.of("<gray>Throw <white><goal-amount></white> <white><item-type></white>.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("item-type", new VariableConfig("LIST", List.of("minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl", "minecraft:trident")));
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("8", "16", "32")));
            config.rewards = new Rewards();
            return config;
        }

        public static QuestDefinitionConfig defaultVillagerTrade() {
            QuestDefinitionConfig config = new QuestDefinitionConfig();
            config.id = "village-economy";
            config.type = "VILLAGER_TRADE";
            config.displayName = "<green>Village Economy";
            config.description = List.of("<gray>Complete <white><goal-amount></white> villager trades.");
            config.variables = new LinkedHashMap<>();
            config.variables.put("goal-amount", new VariableConfig("LIST", List.of("5", "10", "20")));
            config.rewards = new Rewards();
            return config;
        }

    }

    @Getter
    @Configuration
    public static class QuestScheduleConfig {
        @Comment({
                "",
                "Optional inclusive begin timestamp. Leave fields blank for no lower bound."
        })
        private QuestScheduleBoundConfig begin = new QuestScheduleBoundConfig();
        @Comment({
                "",
                "Optional exclusive end timestamp. Leave fields blank for no upper bound."
        })
        private QuestScheduleBoundConfig end = new QuestScheduleBoundConfig();
    }

    @Getter
    @Configuration
    public static class QuestScheduleBoundConfig {
        @Comment({
                "",
                "Date in day/month/year format, for example 09/05/2026."
        })
        private String date = "";
        @Comment({
                "",
                "Time in 24-hour HH:mm format, for example 05:00."
        })
        private String time = "";
        @Comment({
                "",
                "Java timezone id for this timestamp, for example America/Halifax or UTC."
        })
        private String timezone = "";
    }

    @Getter
    @Configuration
    public static class QuestDifficultyConfig {
        @Comment({
                "",
                "MiniMessage display name shown for this difficulty."
        })
        private String displayName = "<green><b>EASY";
        @Comment({
                "",
                "MiniMessage lore lines inserted by <difficulty_lore> in quest menu items."
        })
        private List<String> lore = List.of();
        @Comment({
                "",
                "Inventory slot for this difficulty in the daily difficulty picker; -1 uses the menu slot order."
        })
        private int pickerSlot = -1;
        @Comment({
                "",
                "Inventory slot for this difficulty in the milestone difficulty selector; -1 uses the menu slot order."
        })
        private int milestonesSlot = -1;
        @Comment({
                "",
                "Completed quest requirements needed before players can select this difficulty.",
                "Keys are difficulty ids and values are the number of completed quests needed for that difficulty."
        })
        private Map<String, Integer> requirements = Map.of();
        @Comment({
                "",
                "Random baseline reward command pool; one command is selected when a quest of this difficulty is completed."
        })
        private Rewards rewards = new Rewards(List.of("eco give <player> 250"));
        @Comment({
                "",
                "Composed milestone list for this difficulty; source milestones live in difficulty/<id>/milestones.yml."
        })
        private List<MilestoneConfig> milestones = List.of();

        public QuestDifficultyConfig() {
        }

        public QuestDifficultyConfig(String displayName, Rewards rewards) {
            this.displayName = displayName;
            this.rewards = rewards;
        }

        public QuestDifficultyConfig(String displayName, List<String> lore, Rewards rewards) {
            this.displayName = displayName;
            this.lore = lore;
            this.rewards = rewards;
        }

        public QuestDifficultyConfig(String displayName, List<String> lore, int pickerSlot, int milestonesSlot, Rewards rewards) {
            this.displayName = displayName;
            this.lore = lore;
            this.pickerSlot = pickerSlot;
            this.milestonesSlot = milestonesSlot;
            this.rewards = rewards;
        }
    }

    @Getter
    @Configuration
    public static class MilestoneConfig {
        @Comment({
                "",
                "Per-difficulty completed quest count required to trigger this milestone."
        })
        private int completed = 5;
        @Comment({
                "",
                "MiniMessage display name for this quest completion milestone."
        })
        private String displayName = "<green>Milestone";
        @Comment({
                "",
                "Extra MiniMessage lore lines appended to this quest completion milestone's GUI item."
        })
        private List<String> lore = List.of();
        @Comment({
                "",
                "Console commands run once when this quest completion milestone is reached."
        })
        private List<String> commands = List.of();

        public MilestoneConfig() {
        }

        public MilestoneConfig(int completed, String displayName, List<String> commands) {
            this(completed, displayName, List.of(), commands);
        }

        public MilestoneConfig(int completed, String displayName, List<String> lore, List<String> commands) {
            this.completed = completed;
            this.displayName = displayName;
            this.lore = lore;
            this.commands = commands;
        }
    }

    @Getter
    @Configuration
    public static class VariableConfig {
        @Comment({
                "",
                "Variable selector type. Currently supported: LIST."
        })
        private String selector = "LIST";
        @Comment({
                "",
                "Values available to the selector when a quest is generated."
        })
        private List<String> values = List.of();

        public VariableConfig() {
        }

        public VariableConfig(String selector, List<String> values) {
            this.selector = selector;
            this.values = values;
        }
    }

    @Getter
    @Configuration
    public static class Rewards {
        @Comment({
                "",
                "Console commands run for this reward set; supports quest reward placeholders."
        })
        private List<String> commands = List.of();

        public Rewards() {
        }

        public Rewards(List<String> commands) {
            this.commands = commands;
        }
    }

    private static Map<String, QuestDifficultyConfig> defaultDifficulties() {
        Map<String, QuestDifficultyConfig> difficulties = new LinkedHashMap<>();
        QuestDifficultyConfig easy = new QuestDifficultyConfig("<green><b>EASY", new Rewards(List.of("eco give <player> 250")));
        easy.milestones = List.of(
                new MilestoneConfig(5, "<green>Easy I", List.of("<gray>Complete easy quests to unlock this reward."), List.of("eco give <player> 1000")),
                new MilestoneConfig(10, "<green>Easy II", List.of("<gray>Keep completing easy quests for larger rewards."), List.of("eco give <player> 2500"))
        );
        difficulties.put("easy", easy);
        return difficulties;
    }

    private static List<MilestoneConfig> defaultEasyMilestones() {
        return List.of(
                new MilestoneConfig(5, "<green>Easy I", List.of("<gray>Complete easy quests to unlock this reward."), List.of("eco give <player> 1000")),
                new MilestoneConfig(10, "<green>Easy II", List.of("<gray>Keep completing easy quests for larger rewards."), List.of("eco give <player> 2500"))
        );
    }

    private static List<QuestDefinitionConfig> defaultGlobalQuestDefinitions() {
        return List.of(
                globalQuest(QuestDefinitionConfig.defaultKillAllMobs(), "global-kill-all-mobs", "KILL_ALL_MOBS", "<green>Global Mob Slayer", List.of("2500", "5000", "10000")),
                globalQuest(QuestDefinitionConfig.defaultBreakAllBlocks(), "global-break-all-blocks", "BREAK_ALL_BLOCKS", "<green>Global Excavator", List.of("25000", "50000", "100000")),
                globalQuest(QuestDefinitionConfig.defaultPlaceAnyBlock(), "global-place-any-block", "PLACE_ANY_BLOCK", "<green>Global Construction", List.of("25000", "50000", "100000")),
                globalQuest(QuestDefinitionConfig.defaultSmeltAnyItem(), "global-smelt-anything", "SMELT_ANY_ITEM", "<green>Global Furnace Worker", List.of("2500", "5000", "10000")),
                globalQuest(QuestDefinitionConfig.defaultCraftAnyItem(), "global-craft-anything", "CRAFT_ANY_ITEM", "<green>Global Crafter", List.of("2500", "5000", "10000")),
                globalQuest(QuestDefinitionConfig.defaultFish(), "global-angler", "FISH", "<green>Global Angler", List.of("500", "1000", "2500")),
                globalQuest(QuestDefinitionConfig.defaultTravelDistance(), "global-traveler", "TRAVEL_DISTANCE", "<green>Global Traveler", List.of("250000", "500000", "1000000"))
        );
    }

    private static MenuItem defaultFullGlobalRewardItem() {
        return new MenuItem(
                "EMERALD",
                "<green>Full Reward: <reward_display_name>",
                List.of(
                        "<gray>Quest completion: <white>100%</white>",
                        "<gray>Contributor tier: <white>Top <reward_percentile>%</white>"
                )
        );
    }

    private static MenuItem defaultReducedGlobalRewardItem() {
        return new MenuItem(
                "GOLD_INGOT",
                "<yellow>Reduced Reward: <reward_display_name>",
                List.of(
                        "<gray>Quest completion: <white>50% - 99.9%</white>",
                        "<gray>Contributor tier: <white>Top <reward_percentile>%</white>"
                )
        );
    }

    private static QuestDefinitionConfig globalQuest(QuestDefinitionConfig config, String id, String type, String displayName, List<String> goalAmounts) {
        config.id = id;
        config.type = type;
        config.difficulty = "easy";
        config.displayName = displayName;
        Config.VariableConfig goalAmount = config.variables.get("goal-amount");
        if (goalAmount == null) {
            config.variables.put("goal-amount", new VariableConfig("LIST", goalAmounts));
        } else {
            goalAmount.values = goalAmounts;
        }
        return config;
    }

    private static Map<String, Integer> defaultRerollLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("vip", 1);
        limits.put("vipplus", 2);
        limits.put("mvp", 3);
        return limits;
    }

    private static Map<String, Integer> defaultPremiumLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("vip", 1);
        limits.put("vipplus", 2);
        limits.put("mvp", 3);
        return limits;
    }

    private static Map<String, Rewards> defaultPremiumRewards() {
        Map<String, Rewards> rewards = new LinkedHashMap<>();
        rewards.put("easy", new Rewards(List.of("eco give <player> 125")));
        return rewards;
    }

    private static Map<String, List<String>> defaultPremiumLore() {
        Map<String, List<String>> lore = new LinkedHashMap<>();
        lore.put("easy", List.of(
                "<gold>Premium quest",
                "<gray>Includes bonus premium rewards."
        ));
        return lore;
    }

    private static Map<String, String> defaultPremiumLockedRanks() {
        Map<String, String> ranks = new LinkedHashMap<>();
        ranks.put("4", "<green>VIP");
        ranks.put("5", "<aqua>VIP+");
        ranks.put("6", "<gold>MVP");
        return ranks;
    }

    private static DifficultyComposition composeDifficultyDirectories(List<DifficultyDirectory> difficultyDirectories) {
        List<DifficultyDirectory> directories = difficultyDirectories == null || difficultyDirectories.isEmpty()
                ? List.of(new DifficultyDirectory(
                "easy",
                new DifficultyConfig(),
                new QuestsFile(),
                new DifficultyMilestonesFile()
        ))
                : difficultyDirectories;
        Map<String, QuestDifficultyConfig> composedDifficulties = new LinkedHashMap<>();
        List<QuestDefinitionConfig> composedQuests = new java.util.ArrayList<>();

        for (DifficultyDirectory directory : directories) {
            if (directory == null || directory.difficultyId() == null || directory.difficultyId().isBlank()) {
                throw new IllegalArgumentException("Quest difficulty directory id cannot be blank");
            }
            String difficultyId = directory.difficultyId();
            if (composedDifficulties.containsKey(difficultyId)) {
                throw new IllegalArgumentException("Duplicate quest difficulty id: " + difficultyId);
            }

            DifficultyConfig settings = directory.settings() == null ? new DifficultyConfig() : directory.settings();
            DifficultyMilestonesFile milestonesFile = directory.milestones() == null ? new DifficultyMilestonesFile() : directory.milestones();
            QuestDifficultyConfig difficulty = new QuestDifficultyConfig(
                    settings.displayName == null ? difficultyId : settings.displayName,
                    settings.lore == null ? List.of() : List.copyOf(settings.lore),
                    settings.pickerSlot,
                    settings.milestonesSlot,
                    settings.rewards == null ? new Rewards() : settings.rewards
            );
            difficulty.requirements = settings.requirements == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(settings.requirements));
            difficulty.milestones = milestonesFile.milestones == null ? List.of() : List.copyOf(milestonesFile.milestones);
            composedDifficulties.put(difficultyId, difficulty);

            QuestsFile questsFile = directory.quests() == null ? new QuestsFile() : directory.quests();
            List<QuestDefinitionConfig> questDefinitions = questsFile.questDefinitions == null ? List.of() : questsFile.questDefinitions;
            for (QuestDefinitionConfig questDefinition : questDefinitions) {
                if (questDefinition != null) {
                    questDefinition.setDifficultyId(difficultyId);
                    composedQuests.add(questDefinition);
                }
            }
        }

        return new DifficultyComposition(java.util.Collections.unmodifiableMap(composedDifficulties), List.copyOf(composedQuests));
    }

    private static Messages composeMessages(
            SharedMessagesFile sharedMessagesFile,
            DailyMessages dailyMessages,
            MilestoneMessages milestoneMessages,
            StreakMessages streakMessages
    ) {
        Messages messages = new Messages();
        SharedMessagesFile sharedFile = sharedMessagesFile == null ? new SharedMessagesFile() : sharedMessagesFile;
        SharedMessages shared = sharedFile.messages == null ? new SharedMessages() : sharedFile.messages;
        DailyMessages daily = dailyMessages == null ? new DailyMessages() : dailyMessages;
        MilestoneMessages milestone = milestoneMessages == null ? new MilestoneMessages() : milestoneMessages;
        StreakMessages streak = streakMessages == null ? new StreakMessages() : streakMessages;

        messages.usage = shared.usage;
        messages.adminUsage = shared.adminUsage;
        messages.reloadSuccess = shared.reloadSuccess;
        messages.reloadFailed = shared.reloadFailed;
        messages.playerOnly = shared.playerOnly;
        messages.indicatorSelected = shared.indicatorSelected;
        messages.indicatorReset = shared.indicatorReset;
        messages.indicatorUnavailable = shared.indicatorUnavailable;
        messages.indicatorDisabled = shared.indicatorDisabled;
        messages.adminProgress = shared.adminProgress;
        messages.adminProgressNotActive = shared.adminProgressNotActive;
        messages.invalidAdminProgressAmount = shared.invalidAdminProgressAmount;
        messages.invalidQuestType = shared.invalidQuestType;
        messages.playerNotFound = shared.playerNotFound;
        messages.globalQuestRefreshed = shared.globalQuestRefreshed;
        messages.rerollsReset = shared.rerollsReset;
        messages.streakMilestoneLocked = shared.streakMilestoneLocked;
        messages.streakMilestoneCompleted = shared.streakMilestoneCompleted;
        messages.streakMilestoneProgress = shared.streakMilestoneProgress;

        messages.noQuests = daily.noQuests;
        messages.noAvailableQuestForDifficulty = daily.noAvailableQuestForDifficulty;
        messages.difficultyRequirementsNotMet = daily.difficultyRequirementsNotMet;
        messages.noRerollsRemaining = daily.noRerollsRemaining;
        messages.completedQuestCannotReroll = daily.completedQuestCannotReroll;
        messages.questRerolled = daily.questRerolled;
        messages.progressHeader = daily.progressHeader;
        messages.progressLine = daily.progressLine;
        messages.questCompleted = daily.questCompleted;
        messages.questsResetBroadcast = daily.questsResetBroadcast;
        messages.resetPlayer = daily.resetPlayer;
        messages.rerollPlayer = daily.rerollPlayer;
        messages.completeDailyPlayer = daily.completeDailyPlayer;
        messages.questResetNotReady = daily.questResetNotReady;
        messages.questResetProcessing = daily.questResetProcessing;
        messages.questResetSuccess = daily.questResetSuccess;
        messages.questResetPurchaseUnavailable = daily.questResetPurchaseUnavailable;
        messages.questResetLimitReached = daily.questResetLimitReached;
        messages.questResetFailed = daily.questResetFailed;

        messages.completedCount = milestone.completedCount;
        messages.completedDifficultyLine = milestone.completedDifficultyLine;
        messages.milestoneCompleted = milestone.milestoneCompleted;
        messages.milestoneClaimed = milestone.milestoneClaimed;

        messages.streakRecoveryApplied = streak.streakRecoveryApplied;
        messages.streakRecoveryUnavailable = streak.streakRecoveryUnavailable;
        messages.streakCurrencyUpdated = streak.streakCurrencyUpdated;
        messages.progressIndicators = sharedFile.progressIndicators == null ? new ProgressIndicators() : sharedFile.progressIndicators;
        return messages;
    }
}
