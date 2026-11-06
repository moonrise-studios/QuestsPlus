package gg.moonrise.quests.core.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static CompletableFuture<Void> insert(SqlProvider provider, int id) {
        return provider.runAsync(() -> {
            try (Connection connection = provider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO lifecycle_writes (id) VALUES (?)")) {
                statement.setInt(1, id);
                statement.executeUpdate();
            }
        });
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
