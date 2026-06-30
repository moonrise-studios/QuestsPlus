package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SQLDatabaseTest {

    @Test
    public void mysqlStatementsUseDuplicateKeySyntax() {
        SQLDatabase database = new MySQLDatabase();
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
        SQLDatabase database = new MariaDBDatabase();
        String playerQuestUpsert = database.upsertPlayerQuestSql();

        assertTrue(playerQuestUpsert.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(playerQuestUpsert.contains("VALUES(progress)"));
        assertFalse(playerQuestUpsert.contains(" AS inserted"));
    }

    @Test
    public void postgresqlStatementsUseConflictSyntaxWithoutSqliteIgnore() {
        SQLDatabase database = new PostgreSQLDatabase();

        assertTrue(database.upsertPlayerQuestSql().contains("ON CONFLICT(instance_id)"));
        assertTrue(database.insertRewardExecutionSql().contains("DO NOTHING"));
        assertFalse(database.insertRewardExecutionSql().contains("INSERT OR IGNORE"));
    }

    @Test
    public void sqliteStatementsKeepLegacyCompatibleIgnoreSyntax() {
        SQLDatabase database = new SQLiteDatabase();

        assertTrue(database.upsertGlobalQuestSql().contains("ON CONFLICT(period_key)"));
        assertTrue(database.insertStreakMilestoneSql().contains("INSERT OR IGNORE"));
    }

    @Test
    public void networkBackendsUseConfiguredConnectionUrl() {
        Config.Storage storage = new Config.Storage();
        setField(storage.getMariaDb(), "connectionUrl", "jdbc:mariadb://db.example:3307/quests?connectTimeout=1000");
        setField(storage.getMariaDb(), "username", "maria-user");
        setField(storage.getMariaDb(), "password", "maria-pass");
        setField(storage.getMysql(), "connectionUrl", "jdbc:mysql://db.example:3308/quests?connectTimeout=1000");
        setField(storage.getMysql(), "username", "mysql-user");
        setField(storage.getMysql(), "password", "mysql-pass");
        setField(storage.getPostgresql(), "connectionUrl", "jdbc:postgresql://db.example:5433/quests?ssl=false");
        setField(storage.getPostgresql(), "username", "pg-user");
        setField(storage.getPostgresql(), "password", "pg-pass");

        HikariConfig mariaDb = new MariaDBDatabase().hikariConfig(mock(QuestsPlusPlugin.class), storage);
        HikariConfig mysql = new MySQLDatabase().hikariConfig(mock(QuestsPlusPlugin.class), storage);
        HikariConfig postgresql = new PostgreSQLDatabase().hikariConfig(mock(QuestsPlusPlugin.class), storage);

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
