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
        this.config = loadConfig();
    }

    private Config loadConfig() {
        Config.DailyFile daily = load("daily.yml", Config.DailyFile.class);
        List<Config.DifficultyDirectory> difficulties = loadDifficulties();
        Config.StreaksFile streaks = load("streaks.yml", Config.StreaksFile.class);
        Config.SharedMessagesFile messages = load("messages.yml", Config.SharedMessagesFile.class);
        Config.GlobalQuestsFile globalQuests = load("global-quests.yml", Config.GlobalQuestsFile.class);
        Config.PremiumQuestsFile premiumQuests = load("premium_quests.yml", Config.PremiumQuestsFile.class);
        File currenciesFolder = new File(plugin.getDataFolder(), "currencies");
        Config.PlayerPointsCurrency playerPoints = load(currenciesFolder, "playerpoints.yml", Config.PlayerPointsCurrency.class);
        Config.VaultCurrency vault = load(currenciesFolder, "vault.yml", Config.VaultCurrency.class);
        return Config.compose(null, daily, difficulties, streaks, messages, globalQuests, premiumQuests, playerPoints, vault);
    }

    private List<Config.DifficultyDirectory> loadDifficulties() {
        File root = new File(plugin.getDataFolder(), "difficulty");
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("Failed to create QuestsPlus difficulty config folder.");
        }

        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null || directories.length == 0) {
            directories = new File[]{new File(root, "easy")};
        }

        List<Config.DifficultyDirectory> loaded = new ArrayList<>();
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
                    loaded.add(new Config.DifficultyDirectory(
                            difficultyId,
                            load(directory, "settings.yml", Config.DifficultyConfig.class),
                            load(directory, "quests.yml", Config.QuestsFile.class),
                            load(directory, "milestones.yml", Config.DifficultyMilestonesFile.class)
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
            builder.footer("""
                    Author: Codex
                    """);
            return builder;
        };
    }
}
