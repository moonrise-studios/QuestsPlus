package gg.moonrise.quests.infrastructure.persistence.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.feature.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PostgreSQLDialect implements SqlDialect {

    @Override
    public String displayName() {
        return "PostgreSQL";
    }

    @Override
    public HikariConfig hikariConfig(@NotNull QuestsPlusPlugin plugin, @NotNull Storage storage) {
        return NetworkSettings.of(storage.getPostgresql()).hikariConfig("QuestsPlus-PostgreSQL");
    }

    @Override
    public String columnType(@NotNull SqlColumnType type) {
        return switch (type) {
            case UUID -> "VARCHAR(36)";
            case SHORT_TEXT -> "VARCHAR(64)";
            case IDENTIFIER -> "VARCHAR(128)";
            case DISPLAY_TEXT -> "VARCHAR(255)";
            case LONG_TEXT -> "TEXT";
            case INTEGER -> "INTEGER";
        };
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
        return OnConflictSyntax.doNothing(insert, conflictColumns);
    }
}
