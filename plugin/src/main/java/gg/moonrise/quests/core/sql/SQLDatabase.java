package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;

import java.sql.SQLException;

public interface SQLDatabase {

    String displayName();

    HikariConfig hikariConfig(QuestsPlusPlugin plugin, Config.Storage storage);

    void createTables(HikariDataSource source) throws SQLException;

    String upsertPlayerQuestSql();

    String incrementQuestResetSql();

    String incrementQuestRerollSql();

    String incrementDifficultyStatsSql();

    String insertQuestMilestoneSql();

    String upsertGlobalQuestSql();

    String incrementGlobalContributionSql();

    String insertRewardExecutionSql();

    String upsertStreakStateSql();

    String insertStreakMilestoneSql();

    String upsertIndicatorPreferenceSql();
}
