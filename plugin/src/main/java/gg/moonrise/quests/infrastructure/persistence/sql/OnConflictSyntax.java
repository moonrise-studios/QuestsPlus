package gg.moonrise.quests.infrastructure.persistence.sql;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code INSERT ... ON CONFLICT(...) DO UPDATE} syntax shared by SQLite and PostgreSQL.
 */
public final class OnConflictSyntax {

    private OnConflictSyntax() {
    }

    public static String upsert(@NotNull SqlInsert insert, @NotNull List<String> conflictColumns, @NotNull List<SqlAssignment> assignments) {
        String updates = assignments.stream()
                .map(assignment -> assignment.render(column -> "excluded." + column))
                .collect(Collectors.joining(", "));
        return insert.sql() + "\nON CONFLICT(" + String.join(", ", conflictColumns) + ")\nDO UPDATE SET " + updates;
    }

    public static String doNothing(@NotNull SqlInsert insert, @NotNull List<String> conflictColumns) {
        return insert.sql() + "\nON CONFLICT(" + String.join(", ", conflictColumns) + ") DO NOTHING";
    }
}
