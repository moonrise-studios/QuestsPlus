package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;

import java.util.List;

public class MySQLDatabase extends SQLiteDatabase {

    @Override
    public String displayName() {
        return "MySQL";
    }

    @Override
    public HikariConfig hikariConfig(QuestsPlusPlugin plugin, Config.Storage storage) {
        Config.Storage.MySql mysql = storage.getMysql();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(mysql.getConnectionUrl());
        hikariConfig.setUsername(mysql.getUsername());
        hikariConfig.setPassword(mysql.getPassword());
        hikariConfig.setMaximumPoolSize(Math.max(1, mysql.getPoolSize()));
        hikariConfig.setPoolName("QuestsPlus-MySQL");
        return hikariConfig;
    }

    @Override
    public String upsertPlayerQuestSql() {
        return """
                INSERT INTO player_quests (
                    instance_id, player_uuid, reset_key, definition_id, type, difficulty_id, difficulty_display_name, display_name,
                    description, variables, slot_index, premium, goal_amount, progress, completed
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)%s
                ON DUPLICATE KEY UPDATE progress = %s,
                                        completed = %s,
                                        difficulty_id = %s,
                                        difficulty_display_name = %s,
                                        slot_index = %s,
                                        premium = %s
                """.formatted(
                insertedValuesAlias(),
                insertedColumn("progress"),
                insertedColumn("completed"),
                insertedColumn("difficulty_id"),
                insertedColumn("difficulty_display_name"),
                insertedColumn("slot_index"),
                insertedColumn("premium")
        );
    }

    @Override
    public String incrementQuestResetSql() {
        return """
                INSERT INTO player_quest_resets (player_uuid, reset_key, used_count)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE used_count = used_count + 1
                """;
    }

    @Override
    public String incrementQuestRerollSql() {
        return """
                INSERT INTO player_quest_rerolls (player_uuid, reset_key, used_count)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE used_count = used_count + 1
                """;
    }

    @Override
    public String incrementDifficultyStatsSql() {
        return """
                INSERT INTO player_quest_difficulty_stats (player_uuid, difficulty_id, quests_completed)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE quests_completed = quests_completed + 1
                """;
    }

    @Override
    public String insertQuestMilestoneSql() {
        return """
                INSERT IGNORE INTO player_quest_milestones (player_uuid, difficulty_id, completed)
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
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)%s
                ON DUPLICATE KEY UPDATE progress = %s,
                                        completed = %s
                """.formatted(
                insertedValuesAlias(),
                insertedColumn("progress"),
                insertedColumn("completed")
        );
    }

    @Override
    public String incrementGlobalContributionSql() {
        return """
                INSERT INTO global_quest_contributions (instance_id, player_uuid, contribution)
                VALUES (?, ?, ?)%s
                ON DUPLICATE KEY UPDATE contribution = contribution + %s
                """.formatted(
                insertedValuesAlias(),
                insertedColumn("contribution")
        );
    }

    @Override
    public String insertRewardExecutionSql() {
        return """
                INSERT IGNORE INTO global_quest_reward_executions (instance_id, player_uuid, tier_percentile)
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
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)%s
                ON DUPLICATE KEY UPDATE current_streak = %s,
                                        highest_streak = %s,
                                        last_completed_reset_key = %s,
                                        last_evaluated_reset_key = %s,
                                        last_lost_streak = %s,
                                        lost_reset_key = %s,
                                        shield_balance = %s,
                                        recovery_balance = %s
                """.formatted(
                insertedValuesAlias(),
                insertedColumn("current_streak"),
                insertedColumn("highest_streak"),
                insertedColumn("last_completed_reset_key"),
                insertedColumn("last_evaluated_reset_key"),
                insertedColumn("last_lost_streak"),
                insertedColumn("lost_reset_key"),
                insertedColumn("shield_balance"),
                insertedColumn("recovery_balance")
        );
    }

    @Override
    public String insertStreakMilestoneSql() {
        return """
                INSERT IGNORE INTO player_quest_streak_milestones (player_uuid, streak)
                VALUES (?, ?)
                """;
    }

    @Override
    public String upsertIndicatorPreferenceSql() {
        return """
                INSERT INTO player_quest_indicator_preferences (player_uuid, indicator_type, personal_indicator_type, global_indicator_type)
                VALUES (?, ?, ?, ?)%s
                ON DUPLICATE KEY UPDATE personal_indicator_type = %s,
                                        global_indicator_type = %s
                """.formatted(
                insertedValuesAlias(),
                insertedColumn("personal_indicator_type"),
                insertedColumn("global_indicator_type")
        );
    }

    protected String insertedValuesAlias() {
        return " AS inserted";
    }

    protected String insertedColumn(String column) {
        return "inserted." + column;
    }

    @Override
    protected List<ColumnMigration> columnMigrations() {
        return List.of(
                new ColumnMigration("player_quests", "difficulty_id", "VARCHAR(64)"),
                new ColumnMigration("player_quests", "difficulty_display_name", "VARCHAR(255)"),
                new ColumnMigration("player_quests", "slot_index", "INT"),
                new ColumnMigration("player_quests", "premium", "INT NOT NULL DEFAULT 0"),
                new ColumnMigration("player_quest_indicator_preferences", "personal_indicator_type", "VARCHAR(64)"),
                new ColumnMigration("player_quest_indicator_preferences", "global_indicator_type", "VARCHAR(64)")
        );
    }

    @Override
    protected List<String> tableStatements() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS player_quests (
                    instance_id             CHAR(36)     NOT NULL PRIMARY KEY,
                    player_uuid             CHAR(36)     NOT NULL,
                    reset_key               VARCHAR(64)  NOT NULL,
                    definition_id           VARCHAR(128) NOT NULL,
                    type                    VARCHAR(128) NOT NULL,
                    difficulty_id           VARCHAR(64),
                    difficulty_display_name VARCHAR(255),
                    display_name            VARCHAR(255) NOT NULL,
                    description             TEXT         NOT NULL,
                    variables               TEXT         NOT NULL,
                    slot_index              INT,
                    premium                 INT          NOT NULL DEFAULT 0,
                    goal_amount             INT          NOT NULL,
                    progress                INT          NOT NULL,
                    completed               INT          NOT NULL,
                    KEY idx_player_quests_player_reset (player_uuid, reset_key),
                    KEY idx_player_quests_player_reset_slot (player_uuid, reset_key, slot_index),
                    KEY idx_player_quests_completed (player_uuid, reset_key, completed)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_difficulty_stats (
                    player_uuid      CHAR(36)    NOT NULL,
                    difficulty_id    VARCHAR(64) NOT NULL,
                    quests_completed INT         NOT NULL,
                    PRIMARY KEY (player_uuid, difficulty_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_milestones (
                    player_uuid   CHAR(36)    NOT NULL,
                    difficulty_id VARCHAR(64) NOT NULL,
                    completed     INT         NOT NULL,
                    PRIMARY KEY (player_uuid, difficulty_id, completed)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_streaks (
                    player_uuid              CHAR(36)    NOT NULL PRIMARY KEY,
                    current_streak           INT         NOT NULL DEFAULT 0,
                    highest_streak           INT         NOT NULL DEFAULT 0,
                    last_completed_reset_key VARCHAR(64) NOT NULL DEFAULT '',
                    last_evaluated_reset_key VARCHAR(64) NOT NULL DEFAULT '',
                    last_lost_streak         INT         NOT NULL DEFAULT 0,
                    lost_reset_key           VARCHAR(64) NOT NULL DEFAULT '',
                    shield_balance           INT         NOT NULL DEFAULT 0,
                    recovery_balance         INT         NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_streak_milestones (
                    player_uuid CHAR(36) NOT NULL,
                    streak      INT      NOT NULL,
                    PRIMARY KEY (player_uuid, streak)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_rerolls (
                    player_uuid CHAR(36)    NOT NULL,
                    reset_key   VARCHAR(64) NOT NULL,
                    used_count  INT         NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, reset_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_resets (
                    player_uuid CHAR(36)    NOT NULL,
                    reset_key   VARCHAR(64) NOT NULL,
                    used_count  INT         NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, reset_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_indicator_preferences (
                    player_uuid              CHAR(36)    NOT NULL PRIMARY KEY,
                    indicator_type           VARCHAR(64) NOT NULL,
                    personal_indicator_type  VARCHAR(64),
                    global_indicator_type    VARCHAR(64)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS global_quests (
                    instance_id             CHAR(36)     NOT NULL PRIMARY KEY,
                    period_key              VARCHAR(64)  NOT NULL UNIQUE,
                    starts_at               VARCHAR(64)  NOT NULL,
                    ends_at                 VARCHAR(64)  NOT NULL,
                    definition_id           VARCHAR(128) NOT NULL,
                    type                    VARCHAR(128) NOT NULL,
                    difficulty_id           VARCHAR(64),
                    difficulty_display_name VARCHAR(255),
                    display_name            VARCHAR(255) NOT NULL,
                    description             TEXT         NOT NULL,
                    variables               TEXT         NOT NULL,
                    goal_amount             INT          NOT NULL,
                    progress                INT          NOT NULL,
                    completed               INT          NOT NULL,
                    rewards_executed        INT          NOT NULL DEFAULT 0,
                    KEY idx_global_quests_period (period_key),
                    KEY idx_global_quests_end_rewards (ends_at, rewards_executed)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS global_quest_contributions (
                    instance_id  CHAR(36) NOT NULL,
                    player_uuid  CHAR(36) NOT NULL,
                    contribution INT      NOT NULL DEFAULT 0,
                    PRIMARY KEY (instance_id, player_uuid),
                    KEY idx_global_quest_contributions_instance (instance_id, contribution)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS global_quest_reward_executions (
                    instance_id     CHAR(36) NOT NULL,
                    player_uuid     CHAR(36) NOT NULL,
                    tier_percentile INT      NOT NULL,
                    PRIMARY KEY (instance_id, player_uuid, tier_percentile)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """
        );
    }

    @Override
    protected List<String> indexStatements() {
        return List.of();
    }
}
