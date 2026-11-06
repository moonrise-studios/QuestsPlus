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
import gg.moonrise.quests.core.sql.SQLDatabase;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class SqlProvider implements Disableable {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final QuestsPlusPlugin plugin;
    private final ConfigProvider configProvider;

    private final AtomicReference<HikariDataSource> dataSource = new AtomicReference<>();
    private final ExecutorService reloadExecutor = Executors.newSingleThreadExecutor(threadFactory("QuestsPlus-SQL-Reload"));
    private volatile ExecutorService executor;
    private volatile SQLDatabase database;
    private volatile StorageSettings connectedSettings;

    @PostConstruct
    public synchronized void init() {
        reconnect(configProvider.get().getStorage());
    }

    /**
     * Reconnects to the given storage on a dedicated reload thread, so building the pool and running
     * the schema DDL never blocks the caller. Storage settings equal to the connected ones are kept.
     */
    public CompletableFuture<ReloadResult> reload(Config.Storage storage) {
        try {
            return CompletableFuture.supplyAsync(() -> reconnectIfChanged(storage), reloadExecutor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.completedFuture(new ReloadResult.Failed(exception));
        }
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

    public <T> CompletableFuture<T> supplyInTransaction(SqlTransaction<T> transaction) {
        return supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                Throwable failure = null;
                try {
                    T result = transaction.run(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException exception) {
                    failure = exception;
                    rollback(connection, exception);
                    throw exception;
                } finally {
                    restoreAutoCommit(connection, autoCommit, failure);
                }
            }
        });
    }

    public CompletableFuture<Void> runInTransaction(SqlTransactionRunnable transaction) {
        return supplyInTransaction(connection -> {
            transaction.run(connection);
            return null;
        });
    }

    private synchronized ReloadResult reconnectIfChanged(Config.Storage storage) {
        if (isAvailable() && StorageSettings.of(storage).equals(connectedSettings)) {
            return new ReloadResult.Unchanged();
        }
        return reconnect(storage);
    }

    private synchronized ReloadResult reconnect(Config.Storage storage) {
        SQLDatabase newDatabase = SQLDatabase.forStorageType(storage.getType());
        HikariDataSource newDataSource = null;
        try {
            HikariConfig hikariConfig = newDatabase.hikariConfig(plugin, storage);
            newDataSource = new HikariDataSource(hikariConfig);
            newDatabase.createTables(newDataSource);
            activate(newDatabase, newDataSource);
            connectedSettings = StorageSettings.of(storage);
            log.info("Connected to QuestsPlus {} database.", newDatabase.displayName());
            return new ReloadResult.Reconnected();
        } catch (SQLException | RuntimeException exception) {
            log.error("Failed to initialize QuestsPlus {} database.", newDatabase.displayName(), exception);
            if (dataSource.get() != newDataSource) {
                closeDataSource(newDataSource);
            }
            return new ReloadResult.Failed(exception);
        }
    }

    private void activate(SQLDatabase newDatabase, HikariDataSource newDataSource) {
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null) {
            swapDataSource(newDatabase, newDataSource);
            executor = Executors.newSingleThreadExecutor(sqlThreadFactory());
            return;
        }
        // The swap runs as a task on the single SQL thread: work queued before the reload finishes on
        // the old pool before it closes, work queued after runs on the new pool, and SQL work never
        // runs on two threads at once.
        CompletableFuture.runAsync(() -> swapDataSource(newDatabase, newDataSource), currentExecutor).join();
    }

    private void swapDataSource(SQLDatabase newDatabase, HikariDataSource newDataSource) {
        HikariDataSource oldDataSource = dataSource.getAndSet(newDataSource);
        database = newDatabase;
        try {
            closeDataSource(oldDataSource);
        } catch (RuntimeException exception) {
            log.warn("Failed to close the previous QuestsPlus SQL data source.", exception);
        }
    }

    private ThreadFactory sqlThreadFactory() {
        return threadFactory("QuestsPlus-SQL");
    }

    private static ThreadFactory threadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private synchronized void closeResources() {
        reloadExecutor.shutdown();
        ExecutorService currentExecutor = executor;
        executor = null;
        drainExecutor(currentExecutor);

        closeDataSource(dataSource.getAndSet(null));
        database = null;
        connectedSettings = null;
    }

    private void drainExecutor(ExecutorService executorService) {
        if (executorService == null) {
            return;
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                int dropped = executorService.shutdownNow().size();
                log.warn("QuestsPlus SQL tasks did not finish within {} seconds; {} queued task(s) were dropped.", SHUTDOWN_TIMEOUT_SECONDS, dropped);
            }
        } catch (InterruptedException exception) {
            int dropped = executorService.shutdownNow().size();
            log.warn("Interrupted while waiting for QuestsPlus SQL tasks to finish; {} queued task(s) were dropped.", dropped);
            Thread.currentThread().interrupt();
        }
    }

    private void closeDataSource(HikariDataSource source) {
        if (source != null && !source.isClosed()) {
            source.close();
        }
    }

    private void rollback(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean autoCommit, Throwable cause) throws SQLException {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException exception) {
            if (cause == null) {
                throw exception;
            }
            cause.addSuppressed(exception);
        }
    }

    @Override
    public synchronized void onDisable() {
        closeResources();
    }

    /**
     * Outcome of {@link #reload(Config.Storage)}.
     */
    public sealed interface ReloadResult {

        /**
         * Storage settings were unchanged, so the existing connection was kept.
         */
        public record Unchanged() implements ReloadResult {
        }

        /**
         * A new connection pool was created and replaced the previous one.
         */
        public record Reconnected() implements ReloadResult {
        }

        /**
         * The new connection could not be created; the previous connection is still in use.
         */
        public record Failed(Throwable cause) implements ReloadResult {
        }
    }

    private record StorageSettings(
            Config.Storage.StorageType type,
            String databaseFile,
            String connectionUrl,
            String username,
            String password,
            int poolSize
    ) {

        private static StorageSettings of(Config.Storage storage) {
            Config.Storage.StorageType type = storage.getType() == null ? Config.Storage.StorageType.SQLITE : storage.getType();
            return switch (type) {
                case SQLITE -> new StorageSettings(type, storage.getDatabaseFile(), null, null, null, 0);
                case MARIADB -> {
                    Config.Storage.MariaDb settings = storage.getMariaDb();
                    yield settings == null
                            ? new StorageSettings(type, null, null, null, null, 0)
                            : new StorageSettings(type, null, settings.getConnectionUrl(), settings.getUsername(), settings.getPassword(), settings.getPoolSize());
                }
                case MYSQL -> {
                    Config.Storage.MySql settings = storage.getMysql();
                    yield settings == null
                            ? new StorageSettings(type, null, null, null, null, 0)
                            : new StorageSettings(type, null, settings.getConnectionUrl(), settings.getUsername(), settings.getPassword(), settings.getPoolSize());
                }
                case POSTGRESQL -> {
                    Config.Storage.PostgreSql settings = storage.getPostgresql();
                    yield settings == null
                            ? new StorageSettings(type, null, null, null, null, 0)
                            : new StorageSettings(type, null, settings.getConnectionUrl(), settings.getUsername(), settings.getPassword(), settings.getPoolSize());
                }
            };
        }
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        public T get() throws SQLException;
    }

    @FunctionalInterface
    public interface SqlRunnable {
        public void run() throws SQLException;
    }

    @FunctionalInterface
    public interface SqlTransaction<T> {
        public T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlTransactionRunnable {
        public void run(Connection connection) throws SQLException;
    }
}
