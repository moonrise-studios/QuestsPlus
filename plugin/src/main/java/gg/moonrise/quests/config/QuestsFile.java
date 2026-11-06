package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class QuestsFile {
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
