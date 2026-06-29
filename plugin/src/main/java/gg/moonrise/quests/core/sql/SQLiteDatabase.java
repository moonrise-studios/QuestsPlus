package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;

import java.io.File;
import java.util.List;

public class SQLiteDatabase extends AbstractSQLDatabase {

    @Override
    public String displayName() {
        return "SQLite";
    }

    @Override
    public HikariConfig hikariConfig(QuestsPlusPlugin plugin, Config.Storage storage) {
        plugin.getDataFolder().mkdirs();
        File databaseFile = new File(plugin.getDataFolder(), storage.getDatabaseFile());
        File parent = databaseFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setPoolName("QuestsPlus-SQLite");
        return hikariConfig;
    }

    @Override
    public String upsertPlayerQuestSql() {
        return """
                INSERT INTO player_quests (
                    instance_id, player_uuid, reset_key, definition_id, type, difficulty_id, difficulty_display_name, display_name,
                    description, variables, slot_index, premium, goal_amount, progress, completed
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(instance_id)
                DO UPDATE SET progress = excluded.progress, completed = excluded.completed,
                              difficulty_id = excluded.difficulty_id,
                              difficulty_display_name = excluded.difficulty_display_name,
                              slot_index = excluded.slot_index,
                              premium = excluded.premium
                """;
    }

    @Override
    public String incrementQuestResetSql() {
        return """
                INSERT INTO player_quest_resets (player_uuid, reset_key, used_count)
                VALUES (?, ?, 1)
                ON CONFLICT(player_uuid, reset_key)
                DO UPDATE SET used_count = used_count + 1
                """;
    }

    @Override
    public String incrementQuestRerollSql() {
        return """
                INSERT INTO player_quest_rerolls (player_uuid, reset_key, used_count)
                VALUES (?, ?, 1)
                ON CONFLICT(player_uuid, reset_key)
                DO UPDATE SET used_count = used_count + 1
                """;
    }

    @Override
    public String incrementDifficultyStatsSql() {
        return """
                INSERT INTO player_quest_difficulty_stats (player_uuid, difficulty_id, quests_completed)
                VALUES (?, ?, 1)
                ON CONFLICT(player_uuid, difficulty_id)
                DO UPDATE SET quests_completed = quests_completed + 1
                """;
    }

    @Override
    public String insertQuestMilestoneSql() {
        return """
                INSERT OR IGNORE INTO player_quest_milestones (player_uuid, difficulty_id, completed)
                VALUES (?, ?, ?)
                """;
    }

    @Override
    public String upsertGlobalQuestSql() {
        return """
                INSERT INTO global_quests (
                    instance_id, period_key, starts_at, ends_at, definition_id, type, difficulty_id, difficulty_display_name,
                    display_name, description, variables, goal_amount, progress, completed, rewards_executed
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(period_key)
                DO UPDATE SET progress = excluded.progress, completed = excluded.completed
                """;
    }

    @Override
    public String incrementGlobalContributionSql() {
        return """
                INSERT INTO global_quest_contributions (instance_id, player_uuid, contribution)
                VALUES (?, ?, ?)
                ON CONFLICT(instance_id, player_uuid)
                DO UPDATE SET contribution = contribution + excluded.contribution
                """;
    }

    @Override
    public String insertRewardExecutionSql() {
        return """
                INSERT OR IGNORE INTO global_quest_reward_executions (instance_id, player_uuid, tier_percentile)
                VALUES (?, ?, ?)
                """;
    }

    @Override
    public String upsertStreakStateSql() {
        return """
                INSERT INTO player_quest_streaks (
                    player_uuid, current_streak, highest_streak, last_completed_reset_key, last_evaluated_reset_key,
                    last_lost_streak, lost_reset_key, shield_balance, recovery_balance
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    current_streak = excluded.current_streak,
                    highest_streak = excluded.highest_streak,
                    last_completed_reset_key = excluded.last_completed_reset_key,
                    last_evaluated_reset_key = excluded.last_evaluated_reset_key,
                    last_lost_streak = excluded.last_lost_streak,
                    lost_reset_key = excluded.lost_reset_key,
                    shield_balance = excluded.shield_balance,
                    recovery_balance = excluded.recovery_balance
                """;
    }

    @Override
    public String insertStreakMilestoneSql() {
        return """
                INSERT OR IGNORE INTO player_quest_streak_milestones (player_uuid, streak)
                VALUES (?, ?)
                """;
    }

    @Override
    public String upsertIndicatorPreferenceSql() {
        return """
                INSERT INTO player_quest_indicator_preferences (player_uuid, indicator_type, personal_indicator_type, global_indicator_type)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid)
                DO UPDATE SET personal_indicator_type = excluded.personal_indicator_type,
                              global_indicator_type = excluded.global_indicator_type
                """;
    }

    @Override
    protected List<String> tableStatements() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS player_quests (
                    instance_id   TEXT PRIMARY KEY,
                    player_uuid   TEXT NOT NULL,
                    reset_key     TEXT NOT NULL,
                    definition_id TEXT NOT NULL,
                    type          TEXT NOT NULL,
                    difficulty_id TEXT,
                    difficulty_display_name TEXT,
                    display_name  TEXT NOT NULL,
                    description   TEXT NOT NULL,
                    variables     TEXT NOT NULL,
                    slot_index    INTEGER,
                    premium       INTEGER NOT NULL DEFAULT 0,
                    goal_amount   INTEGER NOT NULL,
                    progress      INTEGER NOT NULL,
                    completed     INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_difficulty_stats (
                    player_uuid      TEXT NOT NULL,
                    difficulty_id    TEXT NOT NULL,
                    quests_completed INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, difficulty_id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_milestones (
                    player_uuid   TEXT NOT NULL,
                    difficulty_id TEXT NOT NULL,
                    completed     INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, difficulty_id, completed)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_streaks (
                    player_uuid              TEXT PRIMARY KEY,
                    current_streak           INTEGER NOT NULL DEFAULT 0,
                    highest_streak           INTEGER NOT NULL DEFAULT 0,
                    last_completed_reset_key TEXT NOT NULL DEFAULT '',
                    last_evaluated_reset_key TEXT NOT NULL DEFAULT '',
                    last_lost_streak         INTEGER NOT NULL DEFAULT 0,
                    lost_reset_key           TEXT NOT NULL DEFAULT '',
                    shield_balance           INTEGER NOT NULL DEFAULT 0,
                    recovery_balance         INTEGER NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_streak_milestones (
                    player_uuid TEXT NOT NULL,
                    streak      INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, streak)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_rerolls (
                    player_uuid TEXT NOT NULL,
                    reset_key   TEXT NOT NULL,
                    used_count  INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, reset_key)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_resets (
                    player_uuid TEXT NOT NULL,
                    reset_key   TEXT NOT NULL,
                    used_count  INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, reset_key)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_indicator_preferences (
                    player_uuid    TEXT PRIMARY KEY,
                    indicator_type TEXT NOT NULL,
                    personal_indicator_type TEXT,
                    global_indicator_type TEXT
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS global_quests (
                    instance_id             TEXT PRIMARY KEY,
                    period_key              TEXT NOT NULL UNIQUE,
                    starts_at               TEXT NOT NULL,
                    ends_at                 TEXT NOT NULL,
                    definition_id           TEXT NOT NULL,
                    type                    TEXT NOT NULL,
                    difficulty_id           TEXT,
                    difficulty_display_name TEXT,
                    display_name            TEXT NOT NULL,
                    description             TEXT NOT NULL,
                    variables               TEXT NOT NULL,
                    goal_amount             INTEGER NOT NULL,
                    progress                INTEGER NOT NULL,
                    completed               INTEGER NOT NULL,
                    rewards_executed        INTEGER NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS global_quest_contributions (
                    instance_id  TEXT NOT NULL,
                    player_uuid  TEXT NOT NULL,
                    contribution INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (instance_id, player_uuid)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS global_quest_reward_executions (
                    instance_id     TEXT NOT NULL,
                    player_uuid     TEXT NOT NULL,
                    tier_percentile INTEGER NOT NULL,
                    PRIMARY KEY (instance_id, player_uuid, tier_percentile)
                )
                """
        );
    }

    @Override
    protected List<String> indexStatements() {
        return List.of(
                "CREATE INDEX IF NOT EXISTS idx_player_quests_player_reset ON player_quests(player_uuid, reset_key)",
                "CREATE INDEX IF NOT EXISTS idx_player_quests_player_reset_slot ON player_quests(player_uuid, reset_key, slot_index)",
                "CREATE INDEX IF NOT EXISTS idx_player_quests_completed ON player_quests(player_uuid, reset_key, completed)",
                "CREATE INDEX IF NOT EXISTS idx_global_quests_period ON global_quests(period_key)",
                "CREATE INDEX IF NOT EXISTS idx_global_quests_end_rewards ON global_quests(ends_at, rewards_executed)",
                "CREATE INDEX IF NOT EXISTS idx_global_quest_contributions_instance ON global_quest_contributions(instance_id, contribution)"
        );
    }
}
