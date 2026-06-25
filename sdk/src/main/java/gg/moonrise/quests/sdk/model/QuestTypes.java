package gg.moonrise.quests.sdk.model;

/**
 * Built-in QuestsPlus quest type constants.
 *
 * <p>External plugins may define additional quest types with {@link QuestType#of(String)}.</p>
 */
public final class QuestTypes {

    /** Kill a specific mob type. */
    public static final QuestType KILL_MOB = QuestType.of("KILL_MOB");
    /** Kill a specific mob type in a specific world. */
    public static final QuestType KILL_MOB_IN_WORLD = QuestType.of("KILL_MOB_IN_WORLD");
    /** Break a specific block type. */
    public static final QuestType BLOCK_BREAK = QuestType.of("BLOCK_BREAK");
    /** Enchant an item using an enchantment table. */
    public static final QuestType ENCHANT_ITEM = QuestType.of("ENCHANT_ITEM");
    /** Place a specific block type. */
    public static final QuestType PLACE_BLOCK = QuestType.of("PLACE_BLOCK");
    /** Place any block. */
    public static final QuestType PLACE_ANY_BLOCK = QuestType.of("PLACE_ANY_BLOCK");
    /** Harvest a specific item drop from a broken block. */
    public static final QuestType HARVEST_ITEM = QuestType.of("HARVEST_ITEM");
    /** Shear sheep. */
    public static final QuestType SHEAR_SHEEP = QuestType.of("SHEAR_SHEEP");
    /** Catch items while fishing. */
    public static final QuestType FISH = QuestType.of("FISH");
    /** Eat cake slices. */
    public static final QuestType EAT_CAKE_SLICE = QuestType.of("EAT_CAKE_SLICE");
    /** Craft a specific item. */
    public static final QuestType CRAFT_ITEM = QuestType.of("CRAFT_ITEM");
    /** Craft any item. */
    public static final QuestType CRAFT_ANY_ITEM = QuestType.of("CRAFT_ANY_ITEM");
    /** Dye sheep a specific color. */
    public static final QuestType DYE_SHEEP = QuestType.of("DYE_SHEEP");
    /** Kill any non-player mob. */
    public static final QuestType KILL_ALL_MOBS = QuestType.of("KILL_ALL_MOBS");
    /** Break any block. */
    public static final QuestType BREAK_ALL_BLOCKS = QuestType.of("BREAK_ALL_BLOCKS");
    /** Extract a specific item from a furnace-like block. */
    public static final QuestType SMELT_ITEM = QuestType.of("SMELT_ITEM");
    /** Extract any item from a furnace-like block. */
    public static final QuestType SMELT_ANY_ITEM = QuestType.of("SMELT_ANY_ITEM");
    /** Take a specific brewed item from a brewing stand. */
    public static final QuestType BREW_ITEM = QuestType.of("BREW_ITEM");
    /** Take any brewed item from a brewing stand. */
    public static final QuestType BREW_ANY_ITEM = QuestType.of("BREW_ANY_ITEM");
    /** Travel a configured horizontal block distance. */
    public static final QuestType TRAVEL_DISTANCE = QuestType.of("TRAVEL_DISTANCE");
    /** Milk a configured milkable mob type. */
    public static final QuestType MILK_MOB = QuestType.of("MILK_MOB");
    /** Throw a configured projectile item type. */
    public static final QuestType THROW_ITEM = QuestType.of("THROW_ITEM");
    /** Complete villager or wandering trader trades. */
    public static final QuestType VILLAGER_TRADE = QuestType.of("VILLAGER_TRADE");

    private QuestTypes() {
    }
}
