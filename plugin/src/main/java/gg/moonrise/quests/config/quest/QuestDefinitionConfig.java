package gg.moonrise.quests.config.quest;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Ignore;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Configuration
public class QuestDefinitionConfig {
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

    /**
     * Turns this default quest into an easy global quest with the given id, type, name and goal amounts.
     */
    public QuestDefinitionConfig asGlobalQuest(String id, String type, String displayName, List<String> goalAmounts) {
        this.id = id;
        this.type = type;
        this.difficulty = "easy";
        this.displayName = displayName;
        VariableConfig goalAmount = variables.get("goal-amount");
        variables.put("goal-amount", new VariableConfig(goalAmount == null ? "LIST" : goalAmount.getSelector(), goalAmounts));
        return this;
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
