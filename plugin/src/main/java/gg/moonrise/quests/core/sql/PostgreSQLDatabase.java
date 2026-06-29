package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;

import java.util.List;

public class PostgreSQLDatabase extends SQLiteDatabase {

    @Override
    public String displayName() {
        return "PostgreSQL";
    }

    @Override
    public HikariConfig hikariConfig(QuestsPlusPlugin plugin, Config.Storage storage) {
        Config.Storage.PostgreSql postgresql = storage.getPostgresql();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(postgresql.getConnectionUrl());
        hikariConfig.setUsername(postgresql.getUsername());
        hikariConfig.setPassword(postgresql.getPassword());
        hikariConfig.setMaximumPoolSize(Math.max(1, postgresql.getPoolSize()));
        hikariConfig.setPoolName("QuestsPlus-PostgreSQL");
        return hikariConfig;
    }

    @Override
    public String insertQuestMilestoneSql() {
        return """
                INSERT INTO player_quest_milestones (player_uuid, difficulty_id, completed)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, difficulty_id, completed) DO NOTHING
                """;
    }

    @Override
    public String insertRewardExecutionSql() {
        return """
                INSERT INTO global_quest_reward_executions (instance_id, player_uuid, tier_percentile)
                VALUES (?, ?, ?)
                ON CONFLICT(instance_id, player_uuid, tier_percentile) DO NOTHING
                """;
    }

    @Override
    public String insertStreakMilestoneSql() {
        return """
                INSERT INTO player_quest_streak_milestones (player_uuid, streak)
                VALUES (?, ?)
                ON CONFLICT(player_uuid, streak) DO NOTHING
                """;
    }

    @Override
    protected List<String> tableStatements() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS player_quests (
                    instance_id             VARCHAR(36)  NOT NULL PRIMARY KEY,
                    player_uuid             VARCHAR(36)  NOT NULL,
                    reset_key               VARCHAR(64)  NOT NULL,
                    definition_id           VARCHAR(128) NOT NULL,
                    type                    VARCHAR(128) NOT NULL,
                    difficulty_id           VARCHAR(64),
                    difficulty_display_name VARCHAR(255),
                    display_name            VARCHAR(255) NOT NULL,
                    description             TEXT         NOT NULL,
                    variables               TEXT         NOT NULL,
                    slot_index              INTEGER,
                    premium                 INTEGER      NOT NULL DEFAULT 0,
                    goal_amount             INTEGER      NOT NULL,
                    progress                INTEGER      NOT NULL,
                    completed               INTEGER      NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_difficulty_stats (
                    player_uuid      VARCHAR(36) NOT NULL,
                    difficulty_id    VARCHAR(64) NOT NULL,
                    quests_completed INTEGER     NOT NULL,
                    PRIMARY KEY (player_uuid, difficulty_id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_milestones (
                    player_uuid   VARCHAR(36) NOT NULL,
                    difficulty_id VARCHAR(64) NOT NULL,
                    completed     INTEGER     NOT NULL,
                    PRIMARY KEY (player_uuid, difficulty_id, completed)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_streaks (
                    player_uuid              VARCHAR(36) PRIMARY KEY,
                    current_streak           INTEGER     NOT NULL DEFAULT 0,
                    highest_streak           INTEGER     NOT NULL DEFAULT 0,
                    last_completed_reset_key VARCHAR(64) NOT NULL DEFAULT '',
                    last_evaluated_reset_key VARCHAR(64) NOT NULL DEFAULT '',
                    last_lost_streak         INTEGER     NOT NULL DEFAULT 0,
                    lost_reset_key           VARCHAR(64) NOT NULL DEFAULT '',
                    shield_balance           INTEGER     NOT NULL DEFAULT 0,
                    recovery_balance         INTEGER     NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_streak_milestones (
                    player_uuid VARCHAR(36) NOT NULL,
                    streak      INTEGER     NOT NULL,
                    PRIMARY KEY (player_uuid, streak)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_rerolls (
                    player_uuid VARCHAR(36) NOT NULL,
                    reset_key   VARCHAR(64) NOT NULL,
                    used_count  INTEGER     NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, reset_key)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_resets (
                    player_uuid VARCHAR(36) NOT NULL,
                    reset_key   VARCHAR(64) NOT NULL,
                    used_count  INTEGER     NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, reset_key)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_quest_indicator_preferences (
                    player_uuid              VARCHAR(36) PRIMARY KEY,
                    indicator_type           VARCHAR(64) NOT NULL,
                    personal_indicator_type  VARCHAR(64),
                    global_indicator_type    VARCHAR(64)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS global_quests (
                    instance_id             VARCHAR(36)  NOT NULL PRIMARY KEY,
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
                    goal_amount             INTEGER      NOT NULL,
                    progress                INTEGER      NOT NULL,
                    completed               INTEGER      NOT NULL,
                    rewards_executed        INTEGER      NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS global_quest_contributions (
                    instance_id  VARCHAR(36) NOT NULL,
                    player_uuid  VARCHAR(36) NOT NULL,
                    contribution INTEGER     NOT NULL DEFAULT 0,
                    PRIMARY KEY (instance_id, player_uuid)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS global_quest_reward_executions (
                    instance_id     VARCHAR(36) NOT NULL,
                    player_uuid     VARCHAR(36) NOT NULL,
                    tier_percentile INTEGER     NOT NULL,
                    PRIMARY KEY (instance_id, player_uuid, tier_percentile)
                )
                """
        );
    }
}
