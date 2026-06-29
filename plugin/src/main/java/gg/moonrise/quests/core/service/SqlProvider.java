package gg.moonrise.quests.core.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.sql.MariaDBDatabase;
import gg.moonrise.quests.core.sql.MySQLDatabase;
import gg.moonrise.quests.core.sql.PostgreSQLDatabase;
import gg.moonrise.quests.core.sql.SQLDatabase;
import gg.moonrise.quests.core.sql.SQLiteDatabase;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class SqlProvider implements Disableable {

    private final QuestsPlusPlugin plugin;
    private final ConfigProvider configProvider;

    private final AtomicReference<HikariDataSource> dataSource = new AtomicReference<>();
    private ExecutorService executor;
    private volatile SQLDatabase database;

    @PostConstruct
    public synchronized void init() {
        reconnect();
    }

    public synchronized boolean reload() {
        return reconnect();
    }

    public boolean isAvailable() {
        HikariDataSource current = dataSource.get();
        return current != null && !current.isClosed() && database != null;
    }

    public Connection getConnection() throws SQLException {
        HikariDataSource current = dataSource.get();
        if (current == null || current.isClosed()) {
            throw new SQLException("SQL data source is not available");
        }
        return current.getConnection();
    }

    public SQLDatabase currentDatabase() {
        SQLDatabase current = database;
        if (current == null) {
            throw new IllegalStateException("SQL database is not available");
        }
        return current;
    }

    public <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> supplier) {
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isShutdown() || !isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("SQL executor is not available"));
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
            return CompletableFuture.failedFuture(new IllegalStateException("SQL executor is not available", exception));
        }
    }

    public CompletableFuture<Void> runAsync(SqlRunnable runnable) {
        return supplyAsync(() -> {
            runnable.run();
            return null;
        });
    }

    public String upsertPlayerQuestSql() {
        return currentDatabase().upsertPlayerQuestSql();
    }

    public String incrementQuestResetSql() {
        return currentDatabase().incrementQuestResetSql();
    }

    public String incrementQuestRerollSql() {
        return currentDatabase().incrementQuestRerollSql();
    }

    public String incrementDifficultyStatsSql() {
        return currentDatabase().incrementDifficultyStatsSql();
    }

    public String insertQuestMilestoneSql() {
        return currentDatabase().insertQuestMilestoneSql();
    }

    public String upsertGlobalQuestSql() {
        return currentDatabase().upsertGlobalQuestSql();
    }

    public String incrementGlobalContributionSql() {
        return currentDatabase().incrementGlobalContributionSql();
    }

    public String insertRewardExecutionSql() {
        return currentDatabase().insertRewardExecutionSql();
    }

    public String upsertStreakStateSql() {
        return currentDatabase().upsertStreakStateSql();
    }

    public String insertStreakMilestoneSql() {
        return currentDatabase().insertStreakMilestoneSql();
    }

    public String upsertIndicatorPreferenceSql() {
        return currentDatabase().upsertIndicatorPreferenceSql();
    }

    private synchronized boolean reconnect() {
        closeResources();

        Config.Storage storage = configProvider.get().getStorage();
        SQLDatabase newDatabase = database(storage);
        HikariDataSource newDataSource = null;
        try {
            HikariConfig hikariConfig = newDatabase.hikariConfig(plugin, storage);
            newDataSource = new HikariDataSource(hikariConfig);
            newDatabase.createTables(newDataSource);
            dataSource.set(newDataSource);
            executor = Executors.newSingleThreadExecutor(sqlThreadFactory());
            database = newDatabase;
            log.info("Connected to QuestsPlus {} database.", newDatabase.displayName());
            return true;
        } catch (SQLException | RuntimeException exception) {
            log.error("Failed to initialize QuestsPlus {} database.", newDatabase.displayName(), exception);
            if (newDataSource != null && !newDataSource.isClosed()) {
                newDataSource.close();
            }
            return false;
        }
    }

    private SQLDatabase database(Config.Storage storage) {
        Config.Storage.StorageType type = storage.getType() == null ? Config.Storage.StorageType.SQLITE : storage.getType();
        return switch (type) {
            case MARIADB -> new MariaDBDatabase();
            case MYSQL -> new MySQLDatabase();
            case POSTGRESQL -> new PostgreSQLDatabase();
            case SQLITE -> new SQLiteDatabase();
        };
    }

    private ThreadFactory sqlThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "QuestsPlus-SQL");
            thread.setDaemon(true);
            return thread;
        };
    }

    private synchronized void closeResources() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }

        HikariDataSource current = dataSource.getAndSet(null);
        if (current != null && !current.isClosed()) {
            current.close();
        }
        database = null;
    }

    @Override
    public synchronized void onDisable() {
        closeResources();
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
