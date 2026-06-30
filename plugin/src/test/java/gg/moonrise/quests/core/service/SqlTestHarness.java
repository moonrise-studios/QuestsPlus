package gg.moonrise.quests.core.service;

import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class SqlTestHarness {

    private static final List<String> TABLES = List.of(
            "global_quest_reward_executions",
            "global_quest_contributions",
            "global_quests",
            "player_quest_indicator_preferences",
            "player_quest_resets",
            "player_quest_rerolls",
            "player_quest_streak_milestones",
            "player_quest_streaks",
            "player_quest_milestones",
            "player_quest_difficulty_stats",
            "player_quests"
    );

    private SqlTestHarness() {
    }

    public static DatabaseTarget sqlite() {
        return new DatabaseTarget("sqlite", "SQLite", Config.Storage.StorageType.SQLITE, null, null, null);
    }

    public static List<DatabaseTarget> configuredExternalTargets() {
        List<DatabaseTarget> targets = new ArrayList<>();
        addExternalTarget(targets, "mariadb", "MariaDB", Config.Storage.StorageType.MARIADB, "QUESTSPLUS_TEST_MARIADB");
        addExternalTarget(targets, "mysql", "MySQL", Config.Storage.StorageType.MYSQL, "QUESTSPLUS_TEST_MYSQL");
        addExternalTarget(targets, "postgresql", "PostgreSQL", Config.Storage.StorageType.POSTGRESQL, "QUESTSPLUS_TEST_POSTGRESQL", "QUESTSPLUS_TEST_POSTGRES");
        return List.copyOf(targets);
    }

    public static SqlProvider openSqlite(Path dataFolder) {
        return open(dataFolder, sqlite());
    }

    public static SqlProvider open(Path dataFolder, DatabaseTarget target) {
        QuestsPlusPlugin plugin = mock(QuestsPlusPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());

        Config config = new Config();
        configureStorage(config.getStorage(), target);

        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);

        SqlProvider provider = new SqlProvider(plugin, configProvider);
        provider.init();

        assertTrue(provider.isAvailable(), () -> target.displayName() + " database should be available");
        if (!target.sqlite()) {
            clearTables(provider);
        }
        return provider;
    }

    public static String externalConfigurationHelp() {
        return "Set QUESTSPLUS_TEST_MARIADB_URL, QUESTSPLUS_TEST_MYSQL_URL, or QUESTSPLUS_TEST_POSTGRESQL_URL to run live network database tests.";
    }

    private static void addExternalTarget(List<DatabaseTarget> targets, String id, String displayName, Config.Storage.StorageType type, String... prefixes) {
        for (String prefix : prefixes) {
            String url = env(prefix + "_URL");
            if (url == null) {
                continue;
            }
            targets.add(new DatabaseTarget(
                    id,
                    displayName,
                    type,
                    url,
                    valueOrDefault(env(prefix + "_USERNAME"), "questsplus"),
                    valueOrDefault(env(prefix + "_PASSWORD"), "change-me")
            ));
            return;
        }
    }

    private static void configureStorage(Config.Storage storage, DatabaseTarget target) {
        setField(storage, "type", target.storageType());
        if (target.sqlite()) {
            return;
        }

        Object settings = switch (target.storageType()) {
            case MARIADB -> storage.getMariaDb();
            case MYSQL -> storage.getMysql();
            case POSTGRESQL -> storage.getPostgresql();
            case SQLITE -> throw new IllegalArgumentException("SQLite target does not use network settings.");
        };
        setField(settings, "connectionUrl", target.connectionUrl());
        setField(settings, "username", target.username());
        setField(settings, "password", target.password());
    }

    private static void clearTables(SqlProvider provider) {
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            for (String table : TABLES) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to clear SQL test tables.", exception);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to configure SQL test field " + name + ".", exception);
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    record DatabaseTarget(
            String id,
            String displayName,
            Config.Storage.StorageType storageType,
            String connectionUrl,
            String username,
            String password
    ) {

        public boolean sqlite() {
            return storageType == Config.Storage.StorageType.SQLITE;
        }

        @Override
        public String toString() {
            return displayName.toLowerCase(Locale.ROOT);
        }
    }
}
