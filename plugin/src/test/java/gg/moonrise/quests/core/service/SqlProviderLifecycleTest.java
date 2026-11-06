package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class SqlProviderLifecycleTest {

    private static final int QUEUED_WRITES = 50;

    @TempDir
    private Path tempDir;

    private SqlProvider sqlProvider;

    @BeforeEach
    public void setUp() {
        sqlProvider = SqliteTestHarness.open(tempDir);
        sqlProvider.runAsync(() -> {
            try (Connection connection = sqlProvider.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE lifecycle_writes (id INTEGER PRIMARY KEY)");
            }
        }).join();
    }

    @AfterEach
    public void tearDown() {
        if (sqlProvider != null) {
            sqlProvider.onDisable();
        }
    }

    @Test
    public void disableDrainsQueuedWritesBeforeClosingDataSource() {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        writes.add(sqlProvider.runAsync(() -> sleep(200)));
        for (int id = 0; id < QUEUED_WRITES; id++) {
            writes.add(insert(sqlProvider, id));
        }

        sqlProvider.onDisable();

        assertFalse(sqlProvider.isAvailable());
        for (CompletableFuture<Void> write : writes) {
            assertTrue(write.isDone(), "queued write should finish before disable returns");
            assertFalse(write.isCompletedExceptionally(), "queued write should not fail on disable");
        }

        sqlProvider = SqliteTestHarness.open(tempDir);
        assertEquals(QUEUED_WRITES, countWrites(sqlProvider));
    }

    @Test
    public void disableRejectsWorkSubmittedAfterShutdown() {
        sqlProvider.onDisable();

        CompletableFuture<Void> late = sqlProvider.runAsync(() -> {
        });

        assertTrue(late.isCompletedExceptionally());
    }

    @Test
    public void reloadKeepsQueuedWritesAndNeverRunsSqlConcurrently() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        writes.add(tracked(running, maxRunning, () -> sleep(200)));
        for (int id = 0; id < QUEUED_WRITES; id++) {
            int rowId = id;
            writes.add(tracked(running, maxRunning, () -> insertNow(sqlProvider, rowId)));
        }

        // A different spelling of the same file forces a real reconnect against the same database.
        assertInstanceOf(SqlProvider.ReloadResult.Reconnected.class, reload(SqlTestHarness.sqliteStorage("storage/./quests.db")));

        for (CompletableFuture<Void> write : writes) {
            assertTrue(write.isDone(), "writes queued before reload should finish before the old pool closes");
            assertFalse(write.isCompletedExceptionally(), "writes queued before reload should not fail");
        }
        for (int id = QUEUED_WRITES; id < QUEUED_WRITES * 2; id++) {
            int rowId = id;
            writes.add(tracked(running, maxRunning, () -> insertNow(sqlProvider, rowId)));
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).orTimeout(10, TimeUnit.SECONDS).join();

        assertTrue(sqlProvider.isAvailable());
        assertEquals(1, maxRunning.get());
        assertEquals(QUEUED_WRITES * 2, countWrites(sqlProvider));
    }

    @Test
    public void keyedWorkKeepsSubmissionOrderPerKeyAndIsDrainedOnDisable() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<String> order = new CopyOnWriteArrayList<>();
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (int id = 0; id < QUEUED_WRITES; id++) {
            int rowId = id;
            UUID key = id % 2 == 0 ? first : second;
            writes.add(sqlProvider.runInTransaction(key, connection -> {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO lifecycle_writes (id) VALUES (?)")) {
                    statement.setInt(1, rowId);
                    statement.executeUpdate();
                }
                order.add(key + ":" + rowId);
            }));
        }

        sqlProvider.onDisable();

        writes.forEach(write -> assertFalse(write.isCompletedExceptionally()));
        for (UUID key : List.of(first, second)) {
            List<String> keyed = order.stream().filter(entry -> entry.startsWith(key.toString())).toList();
            List<String> expected = new ArrayList<>();
            for (int id = key.equals(first) ? 0 : 1; id < QUEUED_WRITES; id += 2) {
                expected.add(key + ":" + id);
            }
            assertEquals(expected, keyed);
        }
        assertTrue(sqlProvider.runAsync(first, () -> {
        }).isCompletedExceptionally());

        sqlProvider = SqliteTestHarness.open(tempDir);
        assertEquals(QUEUED_WRITES, countWrites(sqlProvider));
    }

    @Test
    public void reloadWithUnchangedStorageKeepsConnection() {
        SqlProvider.ReloadResult result = reload(SqlTestHarness.sqliteStorage("storage/quests.db"));

        assertInstanceOf(SqlProvider.ReloadResult.Unchanged.class, result);
        assertTrue(sqlProvider.isAvailable());
        insert(sqlProvider, 1).join();
        assertEquals(1, countWrites(sqlProvider));
    }

    @Test
    public void reloadWithChangedStorageReconnectsOffTheCallingThread() {
        AtomicReference<String> reconnectThread = new AtomicReference<>();
        Config.Storage storage = spy(SqlTestHarness.sqliteStorage("storage/other.db"));
        doAnswer(invocation -> {
            reconnectThread.compareAndSet(null, Thread.currentThread().getName());
            return invocation.callRealMethod();
        }).when(storage).getType();

        SqlProvider.ReloadResult result = reload(storage);

        assertInstanceOf(SqlProvider.ReloadResult.Reconnected.class, result);
        assertEquals("QuestsPlus-SQL-Reload", reconnectThread.get());
        assertTrue(Files.exists(tempDir.resolve("storage/other.db")));
        assertTrue(sqlProvider.isAvailable());
    }

    @Test
    public void failedReloadKeepsPreviousConnection() throws Exception {
        Files.writeString(tempDir.resolve("blocker"), "not a directory");

        SqlProvider.ReloadResult result = reload(SqlTestHarness.sqliteStorage("blocker/quests.db"));

        assertInstanceOf(SqlProvider.ReloadResult.Failed.class, result);
        assertTrue(sqlProvider.isAvailable());
        insert(sqlProvider, 1).orTimeout(10, TimeUnit.SECONDS).join();
        assertEquals(1, countWrites(sqlProvider));
    }

    private SqlProvider.ReloadResult reload(Config.Storage storage) {
        return sqlProvider.reload(storage).orTimeout(30, TimeUnit.SECONDS).join();
    }

    private CompletableFuture<Void> tracked(AtomicInteger running, AtomicInteger maxRunning, SqlProvider.SqlRunnable work) {
        return sqlProvider.runAsync(() -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                work.run();
            } finally {
                running.decrementAndGet();
            }
        });
    }

    private static void insertNow(SqlProvider provider, int id) throws SQLException {
        try (Connection connection = provider.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO lifecycle_writes (id) VALUES (?)")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    private static CompletableFuture<Void> insert(SqlProvider provider, int id) {
        return provider.runAsync(() -> insertNow(provider, id));
    }

    private static int countWrites(SqlProvider provider) {
        return provider.supplyAsync(() -> {
            try (Connection connection = provider.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM lifecycle_writes")) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }).orTimeout(10, TimeUnit.SECONDS).join();
    }

    private static void sleep(long millis) throws SQLException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted", exception);
        }
    }
}
