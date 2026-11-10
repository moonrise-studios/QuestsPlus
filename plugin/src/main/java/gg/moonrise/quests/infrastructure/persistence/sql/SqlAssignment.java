package gg.moonrise.quests.infrastructure.persistence.sql;

import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * One {@code column = expression} assignment applied when an upsert hits an existing row.
 */
public sealed interface SqlAssignment {

    public String column();

    /**
     * Whether the expression reads the value the insert tried to write.
     */
    public boolean usesInsertedValue();

    /**
     * Renders the assignment; {@code insertedValue} maps a column to the dialect's reference to the
     * value the insert tried to write (for example {@code excluded.progress}).
     */
    public String render(@NotNull UnaryOperator<String> insertedValue);

    public static SqlAssignment inserted(@NotNull String column) {
        return new Inserted(column);
    }

    public static SqlAssignment addInserted(@NotNull String column) {
        return new AddInserted(column);
    }

    public static SqlAssignment increment(@NotNull String column) {
        return new Increment(column);
    }

    /**
     * Overwrites the stored value with the inserted one.
     */
    public record Inserted(@NotNull String column) implements SqlAssignment {

        @Override
        public boolean usesInsertedValue() {
            return true;
        }

        @Override
        public String render(@NotNull UnaryOperator<String> insertedValue) {
            return column + " = " + insertedValue.apply(column);
        }
    }

    /**
     * Adds the inserted value to the stored one.
     */
    public record AddInserted(@NotNull String column) implements SqlAssignment {

        @Override
        public boolean usesInsertedValue() {
            return true;
        }

        @Override
        public String render(@NotNull UnaryOperator<String> insertedValue) {
            return column + " = " + column + " + " + insertedValue.apply(column);
        }
    }

    /**
     * Adds one to the stored value.
     */
    public record Increment(@NotNull String column) implements SqlAssignment {

        @Override
        public boolean usesInsertedValue() {
            return false;
        }

        @Override
        public String render(@NotNull UnaryOperator<String> insertedValue) {
            return column + " = " + column + " + 1";
        }
    }
}
