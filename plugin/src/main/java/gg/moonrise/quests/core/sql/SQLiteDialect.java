package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public final class SQLiteDialect implements SqlDialect {

    @Override
    public String displayName() {
        return "SQLite";
    }

    @Override
    public HikariConfig hikariConfig(@NotNull QuestsPlusPlugin plugin, @NotNull Config.Storage storage) {
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
    public String columnType(@NotNull SqlColumnType type) {
        return type == SqlColumnType.INTEGER ? "INTEGER" : "TEXT";
    }

    @Override
    public String tableOptions() {
        return "";
    }

    @Override
    public boolean inlineIndexes() {
        return false;
    }

    @Override
    public String upsert(@NotNull SqlInsert insert, @NotNull List<String> conflictColumns, @NotNull List<SqlAssignment> assignments) {
        return OnConflictSyntax.upsert(insert, conflictColumns, assignments);
    }

    @Override
    public String insertIgnore(@NotNull SqlInsert insert, @NotNull List<String> conflictColumns) {
        return insert.sql("INSERT OR IGNORE INTO");
    }
}
