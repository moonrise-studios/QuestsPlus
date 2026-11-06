package gg.moonrise.quests.core.sql;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A secondary (non-unique) index on {@code tableName}.
 */
public record SqlIndex(@NotNull String tableName, @NotNull String name, @NotNull List<String> columns) {

    public SqlIndex {
        columns = List.copyOf(columns);
    }

    public String columnList() {
        return String.join(", ", columns);
    }
}
