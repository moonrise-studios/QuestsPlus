package gg.moonrise.quests.core.service;

import de.exlll.configlib.NameFormatters;
import gg.moonrise.engine.config.Configuration;
import gg.moonrise.engine.message.Message;
import gg.moonrise.engine.state.Reloadable;
import gg.moonrise.moss.spring.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.Currencies;
import gg.moonrise.quests.config.DailyFile;
import gg.moonrise.quests.config.DifficultyConfig;
import gg.moonrise.quests.config.DifficultyDirectory;
import gg.moonrise.quests.config.DifficultyMilestonesFile;
import gg.moonrise.quests.config.GlobalQuestsFile;
import gg.moonrise.quests.config.PremiumQuestsFile;
import gg.moonrise.quests.config.ProgressIndicatorsFile;
import gg.moonrise.quests.config.QuestMenuFile;
import gg.moonrise.quests.config.QuestMilestonesFile;
import gg.moonrise.quests.config.QuestResetsFile;
import gg.moonrise.quests.config.QuestsFile;
import gg.moonrise.quests.config.SharedMessagesFile;
import gg.moonrise.quests.config.Storage;
import gg.moonrise.quests.config.StreaksFile;
import gg.moonrise.quests.core.yml.MessageSerializer;
import gg.moonrise.quests.util.QuestNames;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@SpringComponent
@RequiredArgsConstructor
public class ConfigProvider implements Reloadable {

    private final QuestsPlusPlugin plugin;

    private volatile Config config;

    @PostConstruct
    public void init() {
        this.config = loadConfig();
    }

    public Config get() {
        return config;
    }

    @Override
    public synchronized void reload() {
        apply(loadConfig());
    }

    /**
     * Reads every config file from disk without making the result active, so callers can finish
     * work that may fail (such as reconnecting storage) before {@link #apply(Config)}.
     */
    public Config loadFromDisk() {
        return loadConfig();
    }

    public synchronized void apply(Config config) {
        this.config = config;
    }

    private Config loadConfig() {
        Storage storage = load("storage-settings.yml", Storage.class);
        DailyFile daily = load("daily.yml", DailyFile.class);
        QuestMenuFile questMenu = load("quest-menu.yml", QuestMenuFile.class);
        List<DifficultyDirectory> difficulties = loadDifficulties();
        StreaksFile streaks = load("streaks.yml", StreaksFile.class);
        SharedMessagesFile messages = load("messages.yml", SharedMessagesFile.class);
        QuestMilestonesFile questMilestones = load("quest-milestones.yml", QuestMilestonesFile.class);
        QuestResetsFile questResets = load("quest-resets.yml", QuestResetsFile.class);
        ProgressIndicatorsFile progressIndicators = load("progress-indicators.yml", ProgressIndicatorsFile.class);
        GlobalQuestsFile globalQuests = load("global-quests.yml", GlobalQuestsFile.class);
        PremiumQuestsFile premiumQuests = load("premium_quests.yml", PremiumQuestsFile.class);
        Currencies currenciesFile = load("currencies.yml", Currencies.class);
        return Config.compose(storage, daily, difficulties, streaks, messages, globalQuests, premiumQuests, questMenu, questMilestones, questResets, progressIndicators, currenciesFile);
    }

    private List<DifficultyDirectory> loadDifficulties() {
        File root = new File(plugin.getDataFolder(), "difficulty");
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("Failed to create QuestsPlus difficulty config folder.");
        }

        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null || directories.length == 0) {
            directories = new File[]{new File(root, "easy")};
        }

        List<DifficultyDirectory> loaded = new ArrayList<>();
        Arrays.stream(directories)
                .sorted(Comparator.comparing(File::getName))
                .forEach(directory -> {
                    if (!directory.exists() && !directory.mkdirs()) {
                        throw new IllegalStateException("Failed to create QuestsPlus difficulty folder " + directory.getName() + ".");
                    }
                    String difficultyId = QuestNames.normalize(directory.getName());
                    if (difficultyId.isBlank()) {
                        throw new IllegalArgumentException("Quest difficulty folder name cannot be blank.");
                    }
                    if (!directory.getName().equals(difficultyId)) {
                        throw new IllegalArgumentException("Quest difficulty folder '" + directory.getName() + "' must be named '" + difficultyId + "'.");
                    }
                    loaded.add(new DifficultyDirectory(
                            difficultyId,
                            load(directory, "settings.yml", DifficultyConfig.class),
                            load(directory, "quests.yml", QuestsFile.class),
                            load(directory, "milestones.yml", DifficultyMilestonesFile.class)
                    ));
                });
        return List.copyOf(loaded);
    }

    private <T> T load(String fileName, Class<T> type) {
        return Configuration.config(new File(plugin.getDataFolder(), fileName), type, properties()).get();
    }

    private <T> T load(File folder, String fileName, Class<T> type) {
        return Configuration.config(new File(folder, fileName), type, properties()).get();
    }

    private Function<de.exlll.configlib.YamlConfigurationProperties.Builder<?>, de.exlll.configlib.YamlConfigurationProperties.Builder<?>> properties() {
        return builder -> {
            builder.setNameFormatter(NameFormatters.LOWER_KEBAB_CASE);
            builder.addSerializer(Message.class, new MessageSerializer());
            builder.inputNulls(true);
            builder.outputNulls(false);
            return builder;
        };
    }
}
