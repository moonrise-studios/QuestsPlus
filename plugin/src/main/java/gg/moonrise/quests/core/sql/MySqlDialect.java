package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.feature.Storage;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MySQL and MariaDB. Both share types, table options and {@code ON DUPLICATE KEY UPDATE}; they
 * differ in connection settings and in how an update refers to the value the insert tried to write.
 */
public final class MySqlDialect implements SqlDialect {

    private static final String TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    private final String displayName;
    private final Function<Storage, NetworkSettings> settings;
    private final InsertedValueSyntax insertedValueSyntax;

    private MySqlDialect(String displayName, Function<Storage, NetworkSettings> settings, InsertedValueSyntax insertedValueSyntax) {
        this.displayName = displayName;
        this.settings = settings;
        this.insertedValueSyntax = insertedValueSyntax;
    }

    public static MySqlDialect mysql() {
        return new MySqlDialect("MySQL", storage -> NetworkSettings.of(storage.getMysql()), InsertedValueSyntax.ROW_ALIAS);
    }

    public static MySqlDialect mariaDb() {
        return new MySqlDialect("MariaDB", storage -> NetworkSettings.of(storage.getMariaDb()), InsertedValueSyntax.VALUES_FUNCTION);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public HikariConfig hikariConfig(@NotNull QuestsPlusPlugin plugin, @NotNull Storage storage) {
        return settings.apply(storage).hikariConfig("QuestsPlus-" + displayName);
    }

    @Override
    public String columnType(@NotNull SqlColumnType type) {
        return switch (type) {
            case UUID -> "CHAR(36)";
            case SHORT_TEXT -> "VARCHAR(64)";
            case IDENTIFIER -> "VARCHAR(128)";
            case DISPLAY_TEXT -> "VARCHAR(255)";
            case LONG_TEXT -> "TEXT";
            case INTEGER -> "INT";
        };
    }

    @Override
    public String tableOptions() {
        return TABLE_OPTIONS;
    }

    @Override
    public boolean inlineIndexes() {
        return true;
    }

    @Override
    public String upsert(@NotNull SqlInsert insert, @NotNull List<String> conflictColumns, @NotNull List<SqlAssignment> assignments) {
        boolean readsInsertedValues = assignments.stream().anyMatch(SqlAssignment::usesInsertedValue);
        String alias = readsInsertedValues ? insertedValueSyntax.rowAlias() : "";
        String updates = assignments.stream()
                .map(assignment -> assignment.render(insertedValueSyntax::reference))
                .collect(Collectors.joining(", "));
        return insert.sql() + alias + "\nON DUPLICATE KEY UPDATE " + updates;
    }

    @Override
    public String insertIgnore(@NotNull SqlInsert insert, @NotNull List<String> conflictColumns) {
        return insert.sql("INSERT IGNORE INTO");
    }

    @Override
    public void dropIndexIfExists(@NotNull Connection connection, @NotNull SqlIndex index) throws SQLException {
        // MySQL has no DROP INDEX IF EXISTS, so look the index up in the current schema first.
        try (PreparedStatement lookup = connection.prepareStatement("""
                SELECT 1
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                """)) {
            lookup.setString(1, index.tableName());
            lookup.setString(2, index.name());
            try (ResultSet resultSet = lookup.executeQuery()) {
                if (!resultSet.next()) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + index.tableName() + " DROP INDEX " + index.name());
        }
    }

    private enum InsertedValueSyntax {
        /**
         * MySQL 8.0.19+: {@code VALUES (...) AS inserted ... inserted.column}; VALUES() is deprecated.
         */
        ROW_ALIAS {
            @Override
            public String rowAlias() {
                return " AS inserted";
            }

            @Override
            public String reference(String column) {
                return "inserted." + column;
            }
        },
        /**
         * MariaDB: no row alias support, {@code VALUES(column)} instead.
         */
        VALUES_FUNCTION {
            @Override
            public String rowAlias() {
                return "";
            }

            @Override
            public String reference(String column) {
                return "VALUES(" + column + ")";
            }
        };

        public abstract String rowAlias();

        public abstract String reference(String column);
    }
}
