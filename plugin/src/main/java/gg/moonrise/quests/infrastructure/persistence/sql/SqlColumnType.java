package gg.moonrise.quests.infrastructure.persistence.sql;

/**
 * Logical column types of the QuestsPlus schema; each {@link SqlDialect} maps them to native types.
 */
public enum SqlColumnType {
    /**
     * A UUID stored as its 36 character string form.
     */
    UUID,
    /**
     * Short keys such as reset keys, period keys, difficulty ids and timestamps (up to 64 characters).
     */
    SHORT_TEXT,
    /**
     * Definition ids and quest type keys (up to 128 characters).
     */
    IDENTIFIER,
    /**
     * MiniMessage display names (up to 255 characters).
     */
    DISPLAY_TEXT,
    /**
     * Unbounded text such as JSON descriptions and variables.
     */
    LONG_TEXT,
    INTEGER
}
