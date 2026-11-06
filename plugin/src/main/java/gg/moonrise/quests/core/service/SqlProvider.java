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
import gg.moonrise.quests.core.sql.KeyedSerialExecutor;
import gg.moonrise.quests.core.sql.SQLDatabase;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.concurrent.Callable;
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
    private static final String SQL_THREAD_NAME = "QuestsPlus-SQL";
    /**
     * Ordering key of work submitted without one: such work runs in submission order.
     */
    private static final Object DEFAULT_KEY = new Object();

    private final QuestsPlusPlugin plugin;
    private final ConfigProvider configProvider;

    private final AtomicReference<HikariDataSource> dataSource = new AtomicReference<>();
    private final ExecutorService reloadExecutor = Executors.newSingleThreadExecutor(threadFactory("QuestsPlus-SQL-Reload"));
    private volatile KeyedSerialExecutor executor;
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

    /**
     * Runs SQL work in submission order relative to all other work submitted without a key.
     */
    public <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> supplier) {
        return supplyAsync(DEFAULT_KEY, supplier);
    }

    /**
     * Runs SQL work on the SQL worker pool. Work submitted with an equal {@code key} (for example a
     * player's UUID) runs one at a time in submission order; work for different keys may run in
     * parallel on networked databases. SQLite has a single worker, so its work never overlaps.
     * A non-empty {@link Collection} key orders the work after earlier work of each of its elements.
     */
    public <T> CompletableFuture<T> supplyAsync(@NotNull Object key, SqlSupplier<T> supplier) {
        KeyedSerialExecutor currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isClosed() || !isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("SQL executor is not available"));
        }
        Callable<T> task = () -> {
            try {
                return supplier.get();
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        };
        CompletableFuture<T> future = key instanceof Collection<?> keys
                ? currentExecutor.submit(keys, task)
                : currentExecutor.submit(key, task);
        if (future.isCompletedExceptionally() && future.exceptionNow() instanceof RejectedExecutionException rejected) {
            return CompletableFuture.failedFuture(new IllegalStateException("SQL executor is not available", rejected));
        }
        return future;
    }

    public CompletableFuture<Void> runAsync(SqlRunnable runnable) {
        return runAsync(DEFAULT_KEY, runnable);
    }

    public CompletableFuture<Void> runAsync(@NotNull Object key, SqlRunnable runnable) {
        return supplyAsync(key, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> supplyInTransaction(SqlTransaction<T> transaction) {
        return supplyInTransaction(DEFAULT_KEY, transaction);
    }

    public <T> CompletableFuture<T> supplyInTransaction(@NotNull Object key, SqlTransaction<T> transaction) {
        return supplyAsync(key, () -> {
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
        return runInTransaction(DEFAULT_KEY, transaction);
    }

    public CompletableFuture<Void> runInTransaction(@NotNull Object key, SqlTransactionRunnable transaction) {
        return supplyInTransaction(key, connection -> {
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
            activate(newDatabase, newDataSource, hikariConfig.getMaximumPoolSize());
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

    /**
     * The worker pool matches the connection pool, so every worker can hold a connection: one worker
     * for SQLite, the configured pool size for networked databases.
     */
    private void activate(SQLDatabase newDatabase, HikariDataSource newDataSource, int workers) {
        KeyedSerialExecutor currentExecutor = executor;
        if (currentExecutor == null) {
            swapDataSource(newDatabase, newDataSource);
            executor = new KeyedSerialExecutor(SQL_THREAD_NAME, workers);
            return;
        }
        // The swap runs as a barrier: work queued before the reload finishes on the old pool before it
        // closes, work queued after runs on the new pool, and no SQL work runs during the swap.
        currentExecutor.barrier(() -> {
            swapDataSource(newDatabase, newDataSource);
            currentExecutor.resize(workers);
        });
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

    private static ThreadFactory threadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private synchronized void closeResources() {
        reloadExecutor.shutdown();
        KeyedSerialExecutor currentExecutor = executor;
        executor = null;
        drainExecutor(currentExecutor);

        closeDataSource(dataSource.getAndSet(null));
        database = null;
        connectedSettings = null;
    }

    private void drainExecutor(KeyedSerialExecutor sqlExecutor) {
        if (sqlExecutor == null) {
            return;
        }
        try {
            if (!sqlExecutor.close(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("QuestsPlus SQL tasks did not finish within {} seconds; unfinished tasks were dropped.", SHUTDOWN_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException exception) {
            log.warn("Interrupted while waiting for QuestsPlus SQL tasks to finish; unfinished tasks were dropped.");
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
