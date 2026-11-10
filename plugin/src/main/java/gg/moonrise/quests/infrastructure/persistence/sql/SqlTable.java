package gg.moonrise.quests.infrastructure.persistence.sql;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A table of the QuestsPlus schema, described independently of any SQL dialect.
 *
 * @param primaryKey primary key columns; a single column is declared inline on that column
 * @param indexes    secondary indexes of this table
 */
public record SqlTable(
        @NotNull String name,
        @NotNull List<SqlColumn> columns,
        @NotNull List<String> primaryKey,
        @NotNull List<SqlIndex> indexes
) {

    public SqlTable {
        columns = List.copyOf(columns);
        primaryKey = List.copyOf(primaryKey);
        indexes = List.copyOf(indexes);
    }

    public Optional<SqlColumn> column(@NotNull String columnName) {
        return columns.stream().filter(column -> column.name().equals(columnName)).findFirst();
    }

    public String createStatement(@NotNull SqlDialect dialect) {
        List<String> definitions = new ArrayList<>();
        boolean inlinePrimaryKey = primaryKey.size() == 1;
        for (SqlColumn column : columns) {
            StringBuilder definition = new StringBuilder(column.name()).append(' ').append(column.definition(dialect));
            if (inlinePrimaryKey && primaryKey.getFirst().equals(column.name())) {
                definition.append(" PRIMARY KEY");
            }
            if (column.unique()) {
                definition.append(" UNIQUE");
            }
            definitions.add(definition.toString());
        }
        if (!inlinePrimaryKey) {
            definitions.add("PRIMARY KEY (" + String.join(", ", primaryKey) + ")");
        }
        if (dialect.inlineIndexes()) {
            for (SqlIndex index : indexes) {
                definitions.add("KEY " + index.name() + " (" + index.columnList() + ")");
            }
        }
        return "CREATE TABLE IF NOT EXISTS " + name + " (\n    "
                + String.join(",\n    ", definitions)
                + "\n)" + dialect.tableOptions();
    }
}
