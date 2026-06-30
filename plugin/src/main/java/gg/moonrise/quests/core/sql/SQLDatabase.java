package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;

import java.sql.SQLException;

public interface SQLDatabase {

    public String displayName();

    public HikariConfig hikariConfig(QuestsPlusPlugin plugin, Config.Storage storage);

    public void createTables(HikariDataSource source) throws SQLException;

    public String upsertPlayerQuestSql();

    public String incrementQuestResetSql();

    public String incrementQuestRerollSql();

    public String incrementDifficultyStatsSql();

    public String insertQuestMilestoneSql();

    public String upsertGlobalQuestSql();

    public String incrementGlobalContributionSql();

    public String insertRewardExecutionSql();

    public String upsertStreakStateSql();

    public String insertStreakMilestoneSql();

    public String upsertIndicatorPreferenceSql();
}
