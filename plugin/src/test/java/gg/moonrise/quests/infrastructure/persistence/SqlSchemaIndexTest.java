package gg.moonrise.quests.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSchemaIndexTest {

    @TempDir
    private Path tempDir;

    private SqlProvider sqlProvider;

    @AfterEach
    public void tearDown() {
        if (sqlProvider != null) {
            sqlProvider.onDisable();
        }
    }

    @Test
    public void newInstallsDoNotCreateRedundantIndexes() throws SQLException {
        sqlProvider = SqliteTestHarness.open(tempDir);

        Set<String> indexes = indexNames(sqlProvider);

        assertFalse(indexes.contains("idx_player_quests_player_reset"));
        assertFalse(indexes.contains("idx_global_quests_period"));
        assertTrue(indexes.contains("idx_player_quests_player_reset_slot"));
        assertTrue(indexes.contains("idx_player_quests_completed"));
        assertTrue(indexes.contains("idx_global_quests_end_rewards"));
        assertTrue(indexes.contains("idx_global_quest_contributions_instance"));
    }

    @Test
    public void existingInstallsDropRedundantIndexesAndKeepUniquePeriodKey() throws SQLException {
        sqlProvider = SqliteTestHarness.open(tempDir);
        execute(sqlProvider, "CREATE INDEX idx_player_quests_player_reset ON player_quests(player_uuid, reset_key)");
        execute(sqlProvider, "CREATE INDEX idx_global_quests_period ON global_quests(period_key)");
        sqlProvider.onDisable();

        sqlProvider = SqliteTestHarness.open(tempDir);
        sqlProvider.onDisable();
        // A second startup must be a no-op once the indexes are gone.
        sqlProvider = SqliteTestHarness.open(tempDir);

        Set<String> indexes = indexNames(sqlProvider);
        assertFalse(indexes.contains("idx_player_quests_player_reset"));
        assertFalse(indexes.contains("idx_global_quests_period"));
        assertTrue(indexes.contains("idx_player_quests_player_reset_slot"));

        execute(sqlProvider, insertGlobalQuest("00000000-0000-0000-0000-000000000001", "week-1"));
        assertThrows(SQLException.class, () -> execute(sqlProvider, insertGlobalQuest("00000000-0000-0000-0000-000000000002", "week-1")));
    }

    @Test
    public void legacyTablesGainColumnsAddedInLaterVersions() throws Exception {
        Files.createDirectories(tempDir.resolve("storage"));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("storage/quests.db"));
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE player_quests (
                        instance_id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, reset_key TEXT NOT NULL, definition_id TEXT NOT NULL,
                        type TEXT NOT NULL, display_name TEXT NOT NULL, description TEXT NOT NULL, variables TEXT NOT NULL,
                        goal_amount INTEGER NOT NULL, progress INTEGER NOT NULL, completed INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE TABLE player_quest_indicator_preferences (player_uuid TEXT PRIMARY KEY, indicator_type TEXT NOT NULL)");
        }

        sqlProvider = SqliteTestHarness.open(tempDir);

        Set<String> questColumns = columnNames(sqlProvider, "player_quests");
        assertTrue(questColumns.containsAll(Set.of("difficulty_id", "difficulty_display_name", "slot_index", "premium")));
        assertTrue(columnNames(sqlProvider, "player_quest_indicator_preferences").containsAll(Set.of("personal_indicator_type", "global_indicator_type")));
        assertTrue(indexNames(sqlProvider).contains("idx_player_quests_player_reset_slot"));
    }

    private static Set<String> columnNames(SqlProvider provider, String table) throws SQLException {
        Set<String> names = new HashSet<>();
        try (Connection connection = provider.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, table, null)) {
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME"));
            }
        }
        return names;
    }

    private static String insertGlobalQuest(String instanceId, String periodKey) {
        return """
                INSERT INTO global_quests (
                    instance_id, period_key, starts_at, ends_at, definition_id, type, display_name, description, variables,
                    goal_amount, progress, completed
                )
                VALUES ('%s', '%s', 'start', 'end', 'definition', 'type', 'name', '[]', '{}', 10, 0, 0)
                """.formatted(instanceId, periodKey);
    }

    private static void execute(SqlProvider provider, String sql) throws SQLException {
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Set<String> indexNames(SqlProvider provider) throws SQLException {
        Set<String> names = new HashSet<>();
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index'")) {
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
        }
        return names;
    }
}
