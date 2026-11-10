package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.feature.Storage;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * The parts of SQL that differ between the supported databases: connection pool settings, native
 * column types, table options and upsert / insert-if-absent syntax. The schema itself lives in
 * {@link QuestSchema} and the statements in {@link SQLDatabase}.
 */
public interface SqlDialect {

    public String displayName();

    public HikariConfig hikariConfig(@NotNull QuestsPlusPlugin plugin, @NotNull Storage storage);

    public String columnType(@NotNull SqlColumnType type);

    /**
     * Options appended after the closing parenthesis of CREATE TABLE.
     */
    public String tableOptions();

    /**
     * Whether secondary indexes are declared inside CREATE TABLE instead of with separate
     * CREATE INDEX IF NOT EXISTS statements.
     */
    public boolean inlineIndexes();

    /**
     * Inserts the row, or applies {@code assignments} when it conflicts with {@code conflictColumns}.
     */
    public String upsert(@NotNull SqlInsert insert, @NotNull List<String> conflictColumns, @NotNull List<SqlAssignment> assignments);

    /**
     * Inserts the row unless it conflicts with {@code conflictColumns}; the update count is 0 when
     * the row already existed.
     */
    public String insertIgnore(@NotNull SqlInsert insert, @NotNull List<String> conflictColumns);

    public default void dropIndexIfExists(@NotNull Connection connection, @NotNull SqlIndex index) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP INDEX IF EXISTS " + index.name());
        }
    }

    public static SqlDialect forStorageType(Storage.StorageType type) {
        return switch (type == null ? Storage.StorageType.SQLITE : type) {
            case MARIADB -> MySqlDialect.mariaDb();
            case MYSQL -> MySqlDialect.mysql();
            case POSTGRESQL -> new PostgreSQLDialect();
            case SQLITE -> new SQLiteDialect();
        };
    }
}
