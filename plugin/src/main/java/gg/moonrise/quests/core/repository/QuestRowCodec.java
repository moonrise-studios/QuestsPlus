package gg.moonrise.quests.core.repository;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared encoding for quest columns stored in SQL: JSON text for descriptions and variables, and
 * the fallbacks used for difficulty columns that older rows left empty.
 */
public final class QuestRowCodec {

    /**
     * Owner id stored on global quests, which belong to no single player.
     */
    public static final UUID GLOBAL_PLAYER_ID = new UUID(0L, 0L);

    public static final String DEFAULT_DIFFICULTY_ID = "easy";
    public static final String DEFAULT_DIFFICULTY_DISPLAY_NAME = "<green><b>EASY";

    private static final Gson GSON = new Gson();

    private QuestRowCodec() {
    }

    public static @NotNull String toJson(@NotNull Object value) {
        return GSON.toJson(value);
    }

    public static @NotNull List<String> stringList(@Nullable String json) {
        String[] values = GSON.fromJson(json, String[].class);
        return values == null ? List.of() : List.of(values);
    }

    public static @NotNull Map<String, String> stringMap(@Nullable String json) {
        JsonObject object = GSON.fromJson(json, JsonObject.class);
        if (object == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            values.put(entry.getKey(), value == null || value.isJsonNull() ? null : value.getAsString());
        }
        return values;
    }

    public static @NotNull String difficultyId(@Nullable String value) {
        return fallback(value, DEFAULT_DIFFICULTY_ID);
    }

    public static @NotNull String difficultyDisplayName(@Nullable String value) {
        return fallback(value, DEFAULT_DIFFICULTY_DISPLAY_NAME);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
