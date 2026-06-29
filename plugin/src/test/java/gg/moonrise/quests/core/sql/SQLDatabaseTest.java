package gg.moonrise.quests.core.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLDatabaseTest {

    @Test
    void mysqlStatementsUseDuplicateKeySyntax() {
        SQLDatabase database = new MySQLDatabase();

        assertTrue(database.upsertPlayerQuestSql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(database.incrementQuestRerollSql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(database.insertQuestMilestoneSql().contains("INSERT IGNORE"));
        assertFalse(database.upsertPlayerQuestSql().contains("ON CONFLICT"));
    }

    @Test
    void postgresqlStatementsUseConflictSyntaxWithoutSqliteIgnore() {
        SQLDatabase database = new PostgreSQLDatabase();

        assertTrue(database.upsertPlayerQuestSql().contains("ON CONFLICT(instance_id)"));
        assertTrue(database.insertRewardExecutionSql().contains("DO NOTHING"));
        assertFalse(database.insertRewardExecutionSql().contains("INSERT OR IGNORE"));
    }

    @Test
    void sqliteStatementsKeepLegacyCompatibleIgnoreSyntax() {
        SQLDatabase database = new SQLiteDatabase();

        assertTrue(database.upsertGlobalQuestSql().contains("ON CONFLICT(period_key)"));
        assertTrue(database.insertStreakMilestoneSql().contains("INSERT OR IGNORE"));
    }
}
