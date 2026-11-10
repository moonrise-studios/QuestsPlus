package gg.moonrise.quests.core.sql;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * A single-row INSERT, rendered by a {@link SqlDialect} into a plain insert, an upsert or an
 * insert-if-absent statement.
 *
 * @param values one SQL expression per column, usually {@code ?}
 */
public record SqlInsert(@NotNull String table, @NotNull List<String> columns, @NotNull List<String> values) {

    public SqlInsert {
        columns = List.copyOf(columns);
        values = List.copyOf(values);
        if (columns.size() != values.size()) {
            throw new IllegalArgumentException("INSERT into " + table + " has " + columns.size() + " columns but " + values.size() + " values");
        }
    }

    /**
     * An insert binding every column to a {@code ?} parameter.
     */
    public static SqlInsert parameters(@NotNull String table, @NotNull List<String> columns) {
        return new SqlInsert(table, columns, Collections.nCopies(columns.size(), "?"));
    }

    public String sql() {
        return sql("INSERT INTO");
    }

    /**
     * Renders the insert with a dialect-specific leading keyword such as {@code INSERT IGNORE INTO}.
     */
    public String sql(@NotNull String insertInto) {
        return insertInto + " " + table + " (" + String.join(", ", columns) + ")\nVALUES (" + String.join(", ", values) + ")";
    }
}
