package gg.moonrise.quests.core.sql;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

import static gg.moonrise.quests.core.sql.SqlColumn.optional;
import static gg.moonrise.quests.core.sql.SqlColumn.required;
import static gg.moonrise.quests.core.sql.SqlColumnType.DISPLAY_TEXT;
import static gg.moonrise.quests.core.sql.SqlColumnType.IDENTIFIER;
import static gg.moonrise.quests.core.sql.SqlColumnType.INTEGER;
import static gg.moonrise.quests.core.sql.SqlColumnType.LONG_TEXT;
import static gg.moonrise.quests.core.sql.SqlColumnType.SHORT_TEXT;
import static gg.moonrise.quests.core.sql.SqlColumnType.UUID;

/**
 * The QuestsPlus schema, described once and rendered for every {@link SqlDialect}.
 */
public final class QuestSchema {

    public static final SqlTable PLAYER_QUESTS = new SqlTable(
            "player_quests",
            List.of(
                    required("instance_id", UUID),
                    required("player_uuid", UUID),
                    required("reset_key", SHORT_TEXT),
                    required("definition_id", IDENTIFIER),
                    required("type", IDENTIFIER),
                    optional("difficulty_id", SHORT_TEXT),
                    optional("difficulty_display_name", DISPLAY_TEXT),
                    required("display_name", DISPLAY_TEXT),
                    required("description", LONG_TEXT),
                    required("variables", LONG_TEXT),
                    optional("slot_index", INTEGER),
                    required("premium", INTEGER).withDefault("0"),
                    required("goal_amount", INTEGER),
                    required("progress", INTEGER),
                    required("completed", INTEGER)
            ),
            List.of("instance_id"),
            List.of(
                    new SqlIndex("player_quests", "idx_player_quests_player_reset_slot", List.of("player_uuid", "reset_key", "slot_index")),
                    new SqlIndex("player_quests", "idx_player_quests_completed", List.of("player_uuid", "reset_key", "completed"))
            )
    );

    public static final SqlTable PLAYER_QUEST_DIFFICULTY_STATS = new SqlTable(
            "player_quest_difficulty_stats",
            List.of(
                    required("player_uuid", UUID),
                    required("difficulty_id", SHORT_TEXT),
                    required("quests_completed", INTEGER)
            ),
            List.of("player_uuid", "difficulty_id"),
            List.of()
    );

    public static final SqlTable PLAYER_QUEST_MILESTONES = new SqlTable(
            "player_quest_milestones",
            List.of(
                    required("player_uuid", UUID),
                    required("difficulty_id", SHORT_TEXT),
                    required("completed", INTEGER)
            ),
            List.of("player_uuid", "difficulty_id", "completed"),
            List.of()
    );

    public static final SqlTable PLAYER_QUEST_STREAKS = new SqlTable(
            "player_quest_streaks",
            List.of(
                    required("player_uuid", UUID),
                    required("current_streak", INTEGER).withDefault("0"),
                    required("highest_streak", INTEGER).withDefault("0"),
                    required("last_completed_reset_key", SHORT_TEXT).withDefault("''"),
                    required("last_evaluated_reset_key", SHORT_TEXT).withDefault("''"),
                    required("last_lost_streak", INTEGER).withDefault("0"),
                    required("lost_reset_key", SHORT_TEXT).withDefault("''"),
                    required("shield_balance", INTEGER).withDefault("0"),
                    required("recovery_balance", INTEGER).withDefault("0")
            ),
            List.of("player_uuid"),
            List.of()
    );

    public static final SqlTable PLAYER_QUEST_STREAK_MILESTONES = new SqlTable(
            "player_quest_streak_milestones",
            List.of(
                    required("player_uuid", UUID),
                    required("streak", INTEGER)
            ),
            List.of("player_uuid", "streak"),
            List.of()
    );

    public static final SqlTable PLAYER_QUEST_REROLLS = usageCounterTable("player_quest_rerolls");

    public static final SqlTable PLAYER_QUEST_RESETS = usageCounterTable("player_quest_resets");

    public static final SqlTable PLAYER_QUEST_INDICATOR_PREFERENCES = new SqlTable(
            "player_quest_indicator_preferences",
            List.of(
                    required("player_uuid", UUID),
                    required("indicator_type", SHORT_TEXT),
                    optional("personal_indicator_type", SHORT_TEXT),
                    optional("global_indicator_type", SHORT_TEXT)
            ),
            List.of("player_uuid"),
            List.of()
    );

    public static final SqlTable GLOBAL_QUESTS = new SqlTable(
            "global_quests",
            List.of(
                    required("instance_id", UUID),
                    required("period_key", SHORT_TEXT).asUnique(),
                    required("starts_at", SHORT_TEXT),
                    required("ends_at", SHORT_TEXT),
                    required("definition_id", IDENTIFIER),
                    required("type", IDENTIFIER),
                    optional("difficulty_id", SHORT_TEXT),
                    optional("difficulty_display_name", DISPLAY_TEXT),
                    required("display_name", DISPLAY_TEXT),
                    required("description", LONG_TEXT),
                    required("variables", LONG_TEXT),
                    required("goal_amount", INTEGER),
                    required("progress", INTEGER),
                    required("completed", INTEGER),
                    required("rewards_executed", INTEGER).withDefault("0")
            ),
            List.of("instance_id"),
            List.of(new SqlIndex("global_quests", "idx_global_quests_end_rewards", List.of("ends_at", "rewards_executed")))
    );

    public static final SqlTable GLOBAL_QUEST_CONTRIBUTIONS = new SqlTable(
            "global_quest_contributions",
            List.of(
                    required("instance_id", UUID),
                    required("player_uuid", UUID),
                    required("contribution", INTEGER).withDefault("0")
            ),
            List.of("instance_id", "player_uuid"),
            List.of(new SqlIndex("global_quest_contributions", "idx_global_quest_contributions_instance", List.of("instance_id", "contribution")))
    );

    public static final SqlTable GLOBAL_QUEST_REWARD_EXECUTIONS = new SqlTable(
            "global_quest_reward_executions",
            List.of(
                    required("instance_id", UUID),
                    required("player_uuid", UUID),
                    required("tier_percentile", INTEGER)
            ),
            List.of("instance_id", "player_uuid", "tier_percentile"),
            List.of()
    );

    public static final List<SqlTable> TABLES = List.of(
            PLAYER_QUESTS,
            PLAYER_QUEST_DIFFICULTY_STATS,
            PLAYER_QUEST_MILESTONES,
            PLAYER_QUEST_STREAKS,
            PLAYER_QUEST_STREAK_MILESTONES,
            PLAYER_QUEST_REROLLS,
            PLAYER_QUEST_RESETS,
            PLAYER_QUEST_INDICATOR_PREFERENCES,
            GLOBAL_QUESTS,
            GLOBAL_QUEST_CONTRIBUTIONS,
            GLOBAL_QUEST_REWARD_EXECUTIONS
    );

    /**
     * Columns added after the first release; existing installs get them through ALTER TABLE.
     */
    private static final List<AddedColumn> ADDED_COLUMNS = List.of(
            new AddedColumn(PLAYER_QUESTS, "difficulty_id"),
            new AddedColumn(PLAYER_QUESTS, "difficulty_display_name"),
            new AddedColumn(PLAYER_QUESTS, "slot_index"),
            new AddedColumn(PLAYER_QUESTS, "premium"),
            new AddedColumn(PLAYER_QUEST_INDICATOR_PREFERENCES, "personal_indicator_type"),
            new AddedColumn(PLAYER_QUEST_INDICATOR_PREFERENCES, "global_indicator_type")
    );

    /**
     * Indexes older versions created that duplicate a primary key, a UNIQUE constraint or a longer
     * index with the same leading columns. They are dropped on startup so existing installs stop
     * paying their write cost; new installs never create them.
     */
    public static final List<SqlIndex> REDUNDANT_INDEXES = List.of(
            new SqlIndex("player_quests", "idx_player_quests_player_reset", List.of("player_uuid", "reset_key")),
            new SqlIndex("global_quests", "idx_global_quests_period", List.of("period_key"))
    );

    private QuestSchema() {
    }

    public static List<String> createTableStatements(@NotNull SqlDialect dialect) {
        return TABLES.stream().map(table -> table.createStatement(dialect)).toList();
    }

    /**
     * Separate CREATE INDEX statements; empty for dialects that declare indexes inside CREATE TABLE.
     */
    public static List<String> createIndexStatements(@NotNull SqlDialect dialect) {
        if (dialect.inlineIndexes()) {
            return List.of();
        }
        return TABLES.stream()
                .flatMap(table -> table.indexes().stream())
                .map(index -> "CREATE INDEX IF NOT EXISTS " + index.name() + " ON " + index.tableName() + "(" + index.columnList() + ")")
                .toList();
    }

    /**
     * Creates missing tables, adds columns introduced by later versions, creates indexes and drops
     * {@link #REDUNDANT_INDEXES}. Every step is idempotent, so this runs on every startup.
     */
    public static void create(@NotNull Connection connection, @NotNull SqlDialect dialect) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : createTableStatements(dialect)) {
                statement.execute(table);
            }
            for (AddedColumn added : ADDED_COLUMNS) {
                addColumnIfMissing(connection, added.table().name(), added.column().name(), added.column().definition(dialect));
            }
            for (String index : createIndexStatements(dialect)) {
                statement.execute(index);
            }
        }
        for (SqlIndex index : REDUNDANT_INDEXES) {
            dialect.dropIndexIfExists(connection, index);
        }
    }

    private static SqlTable usageCounterTable(String name) {
        return new SqlTable(
                name,
                List.of(
                        required("player_uuid", UUID),
                        required("reset_key", SHORT_TEXT),
                        required("used_count", INTEGER).withDefault("0")
                ),
                List.of("player_uuid", "reset_key"),
                List.of()
        );
    }

    private static void addColumnIfMissing(Connection connection, String tableName, String columnName, String columnDefinition) throws SQLException {
        if (hasColumn(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
            return columns.next();
        }
    }

    private record AddedColumn(SqlTable table, SqlColumn column) {

        private AddedColumn(SqlTable table, String columnName) {
            this(table, table.column(columnName).orElseThrow(() -> new IllegalArgumentException("Unknown column " + table.name() + "." + columnName)));
        }
    }
}
