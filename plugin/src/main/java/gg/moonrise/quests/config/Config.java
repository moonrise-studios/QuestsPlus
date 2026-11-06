package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            "Runtime quest progress indicator settings."
    })
    private ProgressIndicators progressIndicators = new ProgressIndicators();

    @Comment({
            "",
            "Quest difficulty tags and their baseline completion rewards."
    })
    private Map<String, QuestDifficultyConfig> questDifficulties = ConfigDefaults.difficulties();

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
            "Whether quest completion milestones are enabled."
    })
    private boolean questMilestonesEnabled = true;

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
    @Comment({
            "",
            "External currency integrations used by QuestsPlus features."
    })
    private Currencies currencies = new Currencies();

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile
    ) {
        return compose(storage, dailyFile, difficultyDirectories, streaksFile, sharedMessagesFile, globalQuestsFile, premiumQuestsFile, (PlayerPointsCurrencyConfig) null, null);
    }

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile,
            PlayerPointsCurrencyConfig playerPointsCurrency,
            VaultCurrencyConfig vaultCurrency
    ) {
        return compose(storage, dailyFile, difficultyDirectories, streaksFile, sharedMessagesFile, globalQuestsFile, premiumQuestsFile, null, playerPointsCurrency, vaultCurrency);
    }

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile,
            QuestResetsFile questResetsFile,
            PlayerPointsCurrencyConfig playerPointsCurrency,
            VaultCurrencyConfig vaultCurrency
    ) {
        return compose(storage, dailyFile, difficultyDirectories, streaksFile, sharedMessagesFile, globalQuestsFile, premiumQuestsFile, null, questResetsFile, playerPointsCurrency, vaultCurrency);
    }

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile,
            QuestMilestonesFile questMilestonesFile,
            QuestResetsFile questResetsFile,
            PlayerPointsCurrencyConfig playerPointsCurrency,
            VaultCurrencyConfig vaultCurrency
    ) {
        return compose(storage, dailyFile, difficultyDirectories, streaksFile, sharedMessagesFile, globalQuestsFile, premiumQuestsFile, questMilestonesFile, questResetsFile, null, new Currencies(null, playerPointsCurrency, vaultCurrency));
    }

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile,
            Currencies currenciesFile
    ) {
        return compose(storage, dailyFile, difficultyDirectories, streaksFile, sharedMessagesFile, globalQuestsFile, premiumQuestsFile, null, currenciesFile);
    }

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile,
            QuestResetsFile questResetsFile,
            Currencies currenciesFile
    ) {
        return compose(storage, dailyFile, difficultyDirectories, streaksFile, sharedMessagesFile, globalQuestsFile, premiumQuestsFile, null, questResetsFile, currenciesFile);
    }

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile,
            QuestMilestonesFile questMilestonesFile,
            QuestResetsFile questResetsFile,
            Currencies currenciesFile
    ) {
        return compose(storage, dailyFile, difficultyDirectories, streaksFile, sharedMessagesFile, globalQuestsFile, premiumQuestsFile, questMilestonesFile, questResetsFile, null, currenciesFile);
    }

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile,
            QuestMilestonesFile questMilestonesFile,
            QuestResetsFile questResetsFile,
            ProgressIndicatorsFile progressIndicatorsFile,
            Currencies currenciesFile
    ) {
        return compose(storage, dailyFile, difficultyDirectories, streaksFile, sharedMessagesFile, globalQuestsFile, premiumQuestsFile, null, questMilestonesFile, questResetsFile, progressIndicatorsFile, currenciesFile);
    }

    public static Config compose(
            Storage storage,
            DailyFile dailyFile,
            List<DifficultyDirectory> difficultyDirectories,
            StreaksFile streaksFile,
            SharedMessagesFile sharedMessagesFile,
            GlobalQuestsFile globalQuestsFile,
            PremiumQuestsFile premiumQuestsFile,
            QuestMenuFile questMenuFile,
            QuestMilestonesFile questMilestonesFile,
            QuestResetsFile questResetsFile,
            ProgressIndicatorsFile progressIndicatorsFile,
            Currencies currenciesFile
    ) {
        Config config = new Config();
        DailyFile daily = dailyFile == null ? new DailyFile() : dailyFile;
        DifficultyComposition difficulties = composeDifficultyDirectories(difficultyDirectories);
        StreaksFile streaks = streaksFile == null ? new StreaksFile() : streaksFile;
        SharedMessagesFile sharedMessages = sharedMessagesFile == null ? new SharedMessagesFile() : sharedMessagesFile;
        QuestMenuFile questMenu = questMenuFile == null ? QuestMenuFile.fromDaily(daily) : questMenuFile;
        QuestMilestonesFile questMilestones = questMilestonesFile == null ? QuestMilestonesFile.fromSharedMessages(sharedMessages) : questMilestonesFile;
        GlobalQuestsFile globalQuests = globalQuestsFile == null ? new GlobalQuestsFile() : globalQuestsFile;
        PremiumQuestsFile premiumQuests = premiumQuestsFile == null ? new PremiumQuestsFile() : premiumQuestsFile;
        QuestResetsFile questResets = questResetsFile == null ? QuestResetsFile.fromMenu(questMenu.getMenu()) : questResetsFile;
        ProgressIndicatorsFile progressIndicators = progressIndicatorsFile == null ? ProgressIndicatorsFile.fromSharedMessages(sharedMessages) : progressIndicatorsFile;
        Currencies currencies = currenciesFile == null ? new Currencies() : currenciesFile;

        config.storage = storage == null ? new Storage() : storage;
        config.daily = daily.toDaily();
        config.menu = questMenu.getMenu() == null ? new QuestMenu() : questMenu.getMenu();
        config.menu.applyQuestResets(questResets);
        config.questDefinitions = difficulties.questDefinitions();
        config.questDifficulties = difficulties.questDifficulties();
        config.questMilestonesEnabled = questMilestones.isEnabled();
        config.milestoneMenu = questMilestones.getMenu() == null ? new MilestoneMenu() : questMilestones.getMenu();
        config.streaks = streaks.toStreaks();
        config.messages = composeMessages(sharedMessages, daily.getMessages(), questMilestones.getMessages(), streaks.getMessages());
        config.progressIndicators = progressIndicators.getProgressIndicators() == null ? new ProgressIndicators() : progressIndicators.getProgressIndicators();
        config.globalQuests = globalQuests;
        config.premiumQuests = premiumQuests;
        config.currencies = currencies;
        return config;
    }

    private record DifficultyComposition(
            Map<String, QuestDifficultyConfig> questDifficulties,
            List<QuestDefinitionConfig> questDefinitions
    ) {
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
        List<QuestDefinitionConfig> composedQuests = new ArrayList<>();

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
                    settings.getDisplayName() == null ? difficultyId : settings.getDisplayName(),
                    settings.getLore() == null ? List.of() : List.copyOf(settings.getLore()),
                    settings.getPickerSlot(),
                    settings.getMilestonesSlot(),
                    settings.getRequirements() == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(settings.getRequirements())),
                    settings.getRewards() == null ? new Rewards() : settings.getRewards(),
                    milestonesFile.getMilestones() == null ? List.of() : List.copyOf(milestonesFile.getMilestones())
            );
            composedDifficulties.put(difficultyId, difficulty);

            QuestsFile questsFile = directory.quests() == null ? new QuestsFile() : directory.quests();
            List<QuestDefinitionConfig> questDefinitions = questsFile.getQuestDefinitions() == null ? List.of() : questsFile.getQuestDefinitions();
            for (QuestDefinitionConfig questDefinition : questDefinitions) {
                if (questDefinition != null) {
                    questDefinition.setDifficultyId(difficultyId);
                    composedQuests.add(questDefinition);
                }
            }
        }

        return new DifficultyComposition(Collections.unmodifiableMap(composedDifficulties), List.copyOf(composedQuests));
    }

    private static Messages composeMessages(
            SharedMessagesFile sharedMessagesFile,
            DailyMessages dailyMessages,
            MilestoneMessages milestoneMessages,
            StreakMessages streakMessages
    ) {
        SharedMessagesFile sharedFile = sharedMessagesFile == null ? new SharedMessagesFile() : sharedMessagesFile;
        return new Messages(sharedFile.getMessages(), dailyMessages, milestoneMessages, streakMessages, sharedFile.getProgressIndicators());
    }
}
