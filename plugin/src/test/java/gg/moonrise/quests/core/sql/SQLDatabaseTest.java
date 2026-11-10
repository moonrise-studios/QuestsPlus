package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.feature.Storage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SQLDatabaseTest {

    @Test
    public void mysqlStatementsUseDuplicateKeySyntax() {
        SQLDatabase database = SQLDatabase.of(MySqlDialect.mysql());
        String playerQuestUpsert = database.upsertPlayerQuestSql();

        assertTrue(playerQuestUpsert.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(playerQuestUpsert.contains(" AS inserted"));
        assertTrue(playerQuestUpsert.contains("inserted.progress"));
        assertFalse(playerQuestUpsert.contains("VALUES(progress)"));
        assertTrue(database.incrementQuestRerollSql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(database.insertQuestMilestoneSql().contains("INSERT IGNORE"));
        assertFalse(playerQuestUpsert.contains("ON CONFLICT"));
    }

    @Test
    public void mariaDbStatementsKeepValuesFunctionForDuplicateKeyUpdates() {
        SQLDatabase database = SQLDatabase.of(MySqlDialect.mariaDb());
        String playerQuestUpsert = database.upsertPlayerQuestSql();

        assertTrue(playerQuestUpsert.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(playerQuestUpsert.contains("VALUES(progress)"));
        assertFalse(playerQuestUpsert.contains(" AS inserted"));
    }

    @Test
    public void postgresqlStatementsUseConflictSyntaxWithoutSqliteIgnore() {
        SQLDatabase database = SQLDatabase.of(new PostgreSQLDialect());

        assertTrue(database.upsertPlayerQuestSql().contains("ON CONFLICT(instance_id)"));
        assertTrue(database.insertRewardExecutionSql().contains("DO NOTHING"));
        assertFalse(database.insertRewardExecutionSql().contains("INSERT OR IGNORE"));
    }

    @Test
    public void sqliteStatementsKeepLegacyCompatibleIgnoreSyntax() {
        SQLDatabase database = SQLDatabase.of(new SQLiteDialect());

        assertTrue(database.upsertGlobalQuestSql().contains("ON CONFLICT(period_key)"));
        assertTrue(database.insertStreakMilestoneSql().contains("INSERT OR IGNORE"));
    }

    @Test
    public void mysqlSchemaDoesNotDeclareRedundantIndexes() {
        String schema = String.join("\n", QuestSchema.createTableStatements(MySqlDialect.mysql()));

        assertFalse(schema.contains("idx_player_quests_player_reset ("));
        assertFalse(schema.contains("idx_global_quests_period"));
        assertTrue(schema.contains("idx_player_quests_player_reset_slot"));
        assertTrue(schema.contains("period_key VARCHAR(64) NOT NULL UNIQUE"));
    }

    @Test
    public void dialectsRenderTheSharedSchemaWithTheirOwnTypesAndIndexPlacement() {
        String sqliteContributions = SQLDatabase.of(new SQLiteDialect()).incrementGlobalContributionSql();
        String mysqlRerolls = SQLDatabase.of(MySqlDialect.mysql()).incrementQuestRerollSql();

        assertEquals("""
                INSERT INTO global_quest_contributions (instance_id, player_uuid, contribution)
                VALUES (?, ?, ?)
                ON CONFLICT(instance_id, player_uuid)
                DO UPDATE SET contribution = contribution + excluded.contribution""", sqliteContributions);
        // Only statements that read the inserted row get the MySQL row alias.
        assertEquals("""
                INSERT INTO player_quest_rerolls (player_uuid, reset_key, used_count)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE used_count = used_count + 1""", mysqlRerolls);

        assertTrue(QuestSchema.PLAYER_QUESTS.createStatement(MySqlDialect.mysql()).contains("instance_id CHAR(36) NOT NULL PRIMARY KEY"));
        assertTrue(QuestSchema.PLAYER_QUESTS.createStatement(new PostgreSQLDialect()).contains("instance_id VARCHAR(36) NOT NULL PRIMARY KEY"));
        assertTrue(QuestSchema.PLAYER_QUESTS.createStatement(new SQLiteDialect()).contains("description TEXT NOT NULL"));
        assertTrue(QuestSchema.PLAYER_QUEST_RESETS.createStatement(new SQLiteDialect()).contains("PRIMARY KEY (player_uuid, reset_key)"));

        assertTrue(QuestSchema.createIndexStatements(MySqlDialect.mariaDb()).isEmpty());
        assertTrue(QuestSchema.PLAYER_QUESTS.createStatement(MySqlDialect.mariaDb()).endsWith(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"));
        assertTrue(QuestSchema.createIndexStatements(new PostgreSQLDialect())
                .contains("CREATE INDEX IF NOT EXISTS idx_global_quests_end_rewards ON global_quests(ends_at, rewards_executed)"));
    }

    @Test
    public void storageTypesSelectMatchingDialects() {
        assertEquals("SQLite", SQLDatabase.forStorageType(null).displayName());
        assertEquals("SQLite", SQLDatabase.forStorageType(Storage.StorageType.SQLITE).displayName());
        assertEquals("MySQL", SQLDatabase.forStorageType(Storage.StorageType.MYSQL).displayName());
        assertEquals("MariaDB", SQLDatabase.forStorageType(Storage.StorageType.MARIADB).displayName());
        assertEquals("PostgreSQL", SQLDatabase.forStorageType(Storage.StorageType.POSTGRESQL).displayName());
    }

    @Test
    public void networkBackendsUseConfiguredConnectionUrl() {
        Storage storage = new Storage();
        setField(storage.getMariaDb(), "connectionUrl", "jdbc:mariadb://db.example:3307/quests?connectTimeout=1000");
        setField(storage.getMariaDb(), "username", "maria-user");
        setField(storage.getMariaDb(), "password", "maria-pass");
        setField(storage.getMysql(), "connectionUrl", "jdbc:mysql://db.example:3308/quests?connectTimeout=1000");
        setField(storage.getMysql(), "username", "mysql-user");
        setField(storage.getMysql(), "password", "mysql-pass");
        setField(storage.getPostgresql(), "connectionUrl", "jdbc:postgresql://db.example:5433/quests?ssl=false");
        setField(storage.getPostgresql(), "username", "pg-user");
        setField(storage.getPostgresql(), "password", "pg-pass");

        HikariConfig mariaDb = MySqlDialect.mariaDb().hikariConfig(mock(QuestsPlusPlugin.class), storage);
        HikariConfig mysql = MySqlDialect.mysql().hikariConfig(mock(QuestsPlusPlugin.class), storage);
        HikariConfig postgresql = new PostgreSQLDialect().hikariConfig(mock(QuestsPlusPlugin.class), storage);

        assertEquals("jdbc:mariadb://db.example:3307/quests?connectTimeout=1000", mariaDb.getJdbcUrl());
        assertEquals("maria-user", mariaDb.getUsername());
        assertEquals("maria-pass", mariaDb.getPassword());
        assertEquals("jdbc:mysql://db.example:3308/quests?connectTimeout=1000", mysql.getJdbcUrl());
        assertEquals("mysql-user", mysql.getUsername());
        assertEquals("mysql-pass", mysql.getPassword());
        assertEquals("jdbc:postgresql://db.example:5433/quests?ssl=false", postgresql.getJdbcUrl());
        assertEquals("pg-user", postgresql.getUsername());
        assertEquals("pg-pass", postgresql.getPassword());
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to configure " + target.getClass().getSimpleName() + "." + name, exception);
        }
    }
}
