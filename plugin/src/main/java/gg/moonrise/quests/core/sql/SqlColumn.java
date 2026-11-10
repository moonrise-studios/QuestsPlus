package gg.moonrise.quests.core.sql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One column of a {@link SqlTable}, described independently of any SQL dialect.
 *
 * @param defaultValue SQL literal used as the column default, or {@code null} for none
 */
public record SqlColumn(
        @NotNull String name,
        @NotNull SqlColumnType type,
        boolean notNull,
        boolean unique,
        @Nullable String defaultValue
) {

    public static SqlColumn required(@NotNull String name, @NotNull SqlColumnType type) {
        return new SqlColumn(name, type, true, false, null);
    }

    public static SqlColumn optional(@NotNull String name, @NotNull SqlColumnType type) {
        return new SqlColumn(name, type, false, false, null);
    }

    public SqlColumn withDefault(@NotNull String literal) {
        return new SqlColumn(name, type, notNull, unique, literal);
    }

    public SqlColumn asUnique() {
        return new SqlColumn(name, type, notNull, true, defaultValue);
    }

    public Optional<String> defaultLiteral() {
        return Optional.ofNullable(defaultValue);
    }

    /**
     * Type, nullability and default as used by both CREATE TABLE and ALTER TABLE ... ADD COLUMN.
     */
    public String definition(@NotNull SqlDialect dialect) {
        StringBuilder definition = new StringBuilder(dialect.columnType(type));
        if (notNull) {
            definition.append(" NOT NULL");
        }
        defaultLiteral().ifPresent(literal -> definition.append(" DEFAULT ").append(literal));
        return definition.toString();
    }
}
