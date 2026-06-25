package gg.moonrise.quests.core.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.QuestsPlusPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class SqliteProvider implements Disableable {

    private final QuestsPlusPlugin plugin;
    private final ConfigProvider configProvider;

    private HikariDataSource dataSource;
    private ExecutorService executor;
    private volatile boolean available;

    @PostConstruct
    public synchronized void init() {
        reconnect();
    }

    public synchronized boolean reload() {
        return reconnect();
    }

    public boolean isAvailable() {
        return available;
    }

    public Connection getConnection() throws SQLException {
        HikariDataSource current = dataSource;
        if (current == null || current.isClosed()) {
            throw new SQLException("SQLite data source is not available");
        }
        return current.getConnection();
    }

    public <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> supplier) {
        ExecutorService currentExecutor = executor;
        if (!available || currentExecutor == null || currentExecutor.isShutdown()) {
            return CompletableFuture.failedFuture(new IllegalStateException("SQLite executor is not available"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return supplier.get();
                } catch (SQLException exception) {
                    throw new RuntimeException(exception);
                }
            }, currentExecutor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new IllegalStateException("SQLite executor is not available", exception));
        }
    }

    public CompletableFuture<Void> runAsync(SqlRunnable runnable) {
        ExecutorService currentExecutor = executor;
        if (!available || currentExecutor == null || currentExecutor.isShutdown()) {
            return CompletableFuture.failedFuture(new IllegalStateException("SQLite executor is not available"));
        }
        try {
            return CompletableFuture.runAsync(() -> {
                try {
                    runnable.run();
                } catch (SQLException exception) {
                    throw new RuntimeException(exception);
                }
            }, currentExecutor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new IllegalStateException("SQLite executor is not available", exception));
        }
    }

    private synchronized boolean reconnect() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            log.error("Failed to create QuestsPlus data folder.");
            return false;
        }

        HikariDataSource previousDataSource = dataSource;
        ExecutorService previousExecutor = executor;
        HikariDataSource nextDataSource = null;
        ExecutorService nextExecutor = Executors.newSingleThreadExecutor();
        try {
            File databaseFile = new File(plugin.getDataFolder(), configProvider.get().getStorage().getDatabaseFile());
            File parentFolder = databaseFile.getParentFile();
            if (parentFolder != null && !parentFolder.exists() && !parentFolder.mkdirs()) {
                log.error("Failed to create QuestsPlus SQLite storage folder {}.", parentFolder.getAbsolutePath());
                nextExecutor.shutdown();
                return false;
            }
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setPoolName("QuestsPlus-SQLite");
            nextDataSource = new HikariDataSource(hikariConfig);
            createTables(nextDataSource);

            dataSource = nextDataSource;
            executor = nextExecutor;
            available = true;

            if (previousExecutor != null) {
                previousExecutor.shutdown();
            }
            if (previousDataSource != null) {
                previousDataSource.close();
            }
            log.info("Connected to QuestsPlus SQLite database.");
            return true;
        } catch (SQLException exception) {
            nextExecutor.shutdown();
            if (nextDataSource != null) {
                nextDataSource.close();
            }
            available = dataSource != null && !dataSource.isClosed();
            log.error("Failed to initialize QuestsPlus SQLite database.", exception);
            return false;
        }
    }

    private void createTables(HikariDataSource source) throws SQLException {
        try (Connection connection = source.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_quests (
                        instance_id   TEXT PRIMARY KEY,
                        player_uuid   TEXT NOT NULL,
                        reset_key     TEXT NOT NULL,
                        definition_id TEXT NOT NULL,
                        type          TEXT NOT NULL,
                        difficulty_id TEXT,
                        difficulty_display_name TEXT,
                        display_name  TEXT NOT NULL,
                        description   TEXT NOT NULL,
                        variables     TEXT NOT NULL,
                        slot_index    INTEGER,
                        premium       INTEGER NOT NULL DEFAULT 0,
                        goal_amount   INTEGER NOT NULL,
                        progress      INTEGER NOT NULL,
                        completed     INTEGER NOT NULL
                    )
                    """);
            addColumnIfMissing(connection, "player_quests", "difficulty_id", "TEXT");
            addColumnIfMissing(connection, "player_quests", "difficulty_display_name", "TEXT");
            addColumnIfMissing(connection, "player_quests", "slot_index", "INTEGER");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_quests_player_reset ON player_quests(player_uuid, reset_key)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_quests_player_reset_slot ON player_quests(player_uuid, reset_key, slot_index)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_quests_completed ON player_quests(player_uuid, reset_key, completed)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_quest_difficulty_stats (
                        player_uuid      TEXT NOT NULL,
                        difficulty_id    TEXT NOT NULL,
                        quests_completed INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, difficulty_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_quest_milestones (
                        player_uuid   TEXT NOT NULL,
                        difficulty_id TEXT NOT NULL,
                        completed     INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, difficulty_id, completed)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_quest_streaks (
                        player_uuid              TEXT PRIMARY KEY,
                        current_streak           INTEGER NOT NULL DEFAULT 0,
                        highest_streak           INTEGER NOT NULL DEFAULT 0,
                        last_completed_reset_key TEXT NOT NULL DEFAULT '',
                        last_evaluated_reset_key TEXT NOT NULL DEFAULT '',
                        last_lost_streak         INTEGER NOT NULL DEFAULT 0,
                        lost_reset_key           TEXT NOT NULL DEFAULT '',
                        shield_balance           INTEGER NOT NULL DEFAULT 0,
                        recovery_balance         INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_quest_streak_milestones (
                        player_uuid TEXT NOT NULL,
                        streak      INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, streak)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_quest_rerolls (
                        player_uuid TEXT NOT NULL,
                        reset_key   TEXT NOT NULL,
                        used_count  INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (player_uuid, reset_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_quest_resets (
                        player_uuid TEXT NOT NULL,
                        reset_key   TEXT NOT NULL,
                        used_count  INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (player_uuid, reset_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_quest_indicator_preferences (
                        player_uuid    TEXT PRIMARY KEY,
                        indicator_type TEXT NOT NULL,
                        personal_indicator_type TEXT,
                        global_indicator_type TEXT
                    )
                    """);
            addColumnIfMissing(connection, "player_quest_indicator_preferences", "personal_indicator_type", "TEXT");
            addColumnIfMissing(connection, "player_quest_indicator_preferences", "global_indicator_type", "TEXT");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS global_quests (
                        instance_id             TEXT PRIMARY KEY,
                        period_key              TEXT NOT NULL UNIQUE,
                        starts_at               TEXT NOT NULL,
                        ends_at                 TEXT NOT NULL,
                        definition_id           TEXT NOT NULL,
                        type                    TEXT NOT NULL,
                        difficulty_id           TEXT,
                        difficulty_display_name TEXT,
                        display_name            TEXT NOT NULL,
                        description             TEXT NOT NULL,
                        variables               TEXT NOT NULL,
                        goal_amount             INTEGER NOT NULL,
                        progress                INTEGER NOT NULL,
                        completed               INTEGER NOT NULL,
                        rewards_executed        INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_global_quests_period ON global_quests(period_key)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_global_quests_end_rewards ON global_quests(ends_at, rewards_executed)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS global_quest_contributions (
                        instance_id  TEXT NOT NULL,
                        player_uuid  TEXT NOT NULL,
                        contribution INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (instance_id, player_uuid)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_global_quest_contributions_instance ON global_quest_contributions(instance_id, contribution)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS global_quest_reward_executions (
                        instance_id     TEXT NOT NULL,
                        player_uuid     TEXT NOT NULL,
                        tier_percentile INTEGER NOT NULL,
                        PRIMARY KEY (instance_id, player_uuid, tier_percentile)
                    )
                    """);
        }
    }

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String columnDefinition) throws SQLException {
        try (java.sql.ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    @Override
    public synchronized void onDisable() {
        available = false;
        if (executor != null) {
            executor.shutdown();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    @FunctionalInterface
    public interface SqlRunnable {
        void run() throws SQLException;
    }
}
