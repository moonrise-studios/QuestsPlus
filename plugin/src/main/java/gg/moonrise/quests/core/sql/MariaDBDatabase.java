package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;

public class MariaDBDatabase extends MySQLDatabase {

    @Override
    public String displayName() {
        return "MariaDB";
    }

    @Override
    public HikariConfig hikariConfig(QuestsPlusPlugin plugin, Config.Storage storage) {
        Config.Storage.MariaDb mariaDb = storage.getMariaDb();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(mariaDb.getConnectionUrl());
        hikariConfig.setUsername(mariaDb.getUsername());
        hikariConfig.setPassword(mariaDb.getPassword());
        hikariConfig.setMaximumPoolSize(Math.max(1, mariaDb.getPoolSize()));
        hikariConfig.setPoolName("QuestsPlus-MariaDB");
        return hikariConfig;
    }

    @Override
    protected String insertedValuesAlias() {
        return "";
    }

    @Override
    protected String insertedColumn(String column) {
        return "VALUES(" + column + ")";
    }
}
