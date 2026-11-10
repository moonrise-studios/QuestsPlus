package gg.moonrise.quests.infrastructure.persistence.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Storage;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static gg.moonrise.quests.infrastructure.persistence.sql.SqlAssignment.addInserted;
import static gg.moonrise.quests.infrastructure.persistence.sql.SqlAssignment.increment;
import static gg.moonrise.quests.infrastructure.persistence.sql.SqlAssignment.inserted;

/**
 * The connected database: its {@link SqlDialect} plus every dialect-specific statement the
 * repositories run, rendered once from the shared {@link QuestSchema}. Statements that are the same
 * on every database live in the repositories themselves.
 */
public record SQLDatabase(
        @NotNull SqlDialect dialect,
        @NotNull String upsertPlayerQuestSql,
        @NotNull String insertQuestResetUsageIfAbsentSql,
        @NotNull String incrementQuestRerollSql,
        @NotNull String incrementDifficultyStatsSql,
        @NotNull String insertQuestMilestoneSql,
        @NotNull String upsertGlobalQuestSql,
        @NotNull String incrementGlobalContributionSql,
        @NotNull String insertRewardExecutionSql,
        @NotNull String upsertStreakStateSql,
        @NotNull String insertStreakMilestoneSql,
        @NotNull String upsertIndicatorPreferenceSql
) {

    public static SQLDatabase forStorageType(Storage.StorageType type) {
        return of(SqlDialect.forStorageType(type));
    }

    public static SQLDatabase of(@NotNull SqlDialect dialect) {
        return new SQLDatabase(
                dialect,
                dialect.upsert(
                        SqlInsert.parameters("player_quests", List.of(
                                "instance_id", "player_uuid", "reset_key", "definition_id", "type", "difficulty_id", "difficulty_display_name", "display_name",
                                "description", "variables", "slot_index", "premium", "goal_amount", "progress", "completed"
                        )),
                        List.of("instance_id"),
                        List.of(inserted("progress"), inserted("completed"), inserted("difficulty_id"), inserted("difficulty_display_name"),
                                inserted("slot_index"), inserted("premium"))
                ),
                dialect.insertIgnore(
                        new SqlInsert("player_quest_resets", List.of("player_uuid", "reset_key", "used_count"), List.of("?", "?", "0")),
                        List.of("player_uuid", "reset_key")
                ),
                dialect.upsert(
                        new SqlInsert("player_quest_rerolls", List.of("player_uuid", "reset_key", "used_count"), List.of("?", "?", "1")),
                        List.of("player_uuid", "reset_key"),
                        List.of(increment("used_count"))
                ),
                dialect.upsert(
                        new SqlInsert("player_quest_difficulty_stats", List.of("player_uuid", "difficulty_id", "quests_completed"), List.of("?", "?", "1")),
                        List.of("player_uuid", "difficulty_id"),
                        List.of(increment("quests_completed"))
                ),
                dialect.insertIgnore(
                        SqlInsert.parameters("player_quest_milestones", List.of("player_uuid", "difficulty_id", "completed")),
                        List.of("player_uuid", "difficulty_id", "completed")
                ),
                dialect.upsert(
                        SqlInsert.parameters("global_quests", List.of(
                                "instance_id", "period_key", "starts_at", "ends_at", "definition_id", "type", "difficulty_id", "difficulty_display_name",
                                "display_name", "description", "variables", "goal_amount", "progress", "completed", "rewards_executed"
                        )),
                        List.of("period_key"),
                        List.of(inserted("progress"), inserted("completed"))
                ),
                dialect.upsert(
                        SqlInsert.parameters("global_quest_contributions", List.of("instance_id", "player_uuid", "contribution")),
                        List.of("instance_id", "player_uuid"),
                        List.of(addInserted("contribution"))
                ),
                dialect.insertIgnore(
                        SqlInsert.parameters("global_quest_reward_executions", List.of("instance_id", "player_uuid", "tier_percentile")),
                        List.of("instance_id", "player_uuid", "tier_percentile")
                ),
                dialect.upsert(
                        SqlInsert.parameters("player_quest_streaks", List.of(
                                "player_uuid", "current_streak", "highest_streak", "last_completed_reset_key", "last_evaluated_reset_key",
                                "last_lost_streak", "lost_reset_key", "shield_balance", "recovery_balance"
                        )),
                        List.of("player_uuid"),
                        List.of(inserted("current_streak"), inserted("highest_streak"), inserted("last_completed_reset_key"),
                                inserted("last_evaluated_reset_key"), inserted("last_lost_streak"), inserted("lost_reset_key"),
                                inserted("shield_balance"), inserted("recovery_balance"))
                ),
                dialect.insertIgnore(
                        SqlInsert.parameters("player_quest_streak_milestones", List.of("player_uuid", "streak")),
                        List.of("player_uuid", "streak")
                ),
                dialect.upsert(
                        SqlInsert.parameters("player_quest_indicator_preferences", List.of(
                                "player_uuid", "indicator_type", "personal_indicator_type", "global_indicator_type"
                        )),
                        List.of("player_uuid"),
                        List.of(inserted("personal_indicator_type"), inserted("global_indicator_type"))
                )
        );
    }

    public String displayName() {
        return dialect.displayName();
    }

    public HikariConfig hikariConfig(@NotNull QuestsPlusPlugin plugin, @NotNull Storage storage) {
        return dialect.hikariConfig(plugin, storage);
    }

    public void createTables(@NotNull HikariDataSource source) throws SQLException {
        try (Connection connection = source.getConnection()) {
            QuestSchema.create(connection, dialect);
        }
    }
}
