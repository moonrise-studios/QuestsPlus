package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

abstract class AbstractSQLDatabase implements SQLDatabase {

    @Override
    public void createTables(HikariDataSource source) throws SQLException {
        try (Connection connection = source.getConnection();
             Statement statement = connection.createStatement()) {
            for (String table : tableStatements()) {
                statement.execute(table);
            }
            for (ColumnMigration migration : columnMigrations()) {
                addColumnIfMissing(connection, migration.tableName(), migration.columnName(), migration.columnDefinition());
            }
            for (String index : indexStatements()) {
                statement.execute(index);
            }
        }
    }

    protected abstract List<String> tableStatements();

    protected List<ColumnMigration> columnMigrations() {
        return List.of(
                new ColumnMigration("player_quests", "difficulty_id", "TEXT"),
                new ColumnMigration("player_quests", "difficulty_display_name", "TEXT"),
                new ColumnMigration("player_quests", "slot_index", "INTEGER"),
                new ColumnMigration("player_quests", "premium", "INTEGER NOT NULL DEFAULT 0"),
                new ColumnMigration("player_quest_indicator_preferences", "personal_indicator_type", "TEXT"),
                new ColumnMigration("player_quest_indicator_preferences", "global_indicator_type", "TEXT")
        );
    }

    protected abstract List<String> indexStatements();

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String columnDefinition) throws SQLException {
        if (hasColumn(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName.toUpperCase(java.util.Locale.ROOT), columnName.toUpperCase(java.util.Locale.ROOT))) {
            return columns.next();
        }
    }

    protected record ColumnMigration(String tableName, String columnName, String columnDefinition) {
    }
}
