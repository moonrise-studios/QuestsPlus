package gg.moonrise.quests.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reads the runtime library coordinates that the build generates from the version catalog.
 */
public final class RuntimeLibraries {

    public static final String RESOURCE = "runtime-libraries.txt";

    private RuntimeLibraries() {
    }

    public static List<String> load() {
        try (InputStream input = RuntimeLibraries.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing QuestsPlus runtime library list " + RESOURCE + " in the plugin jar.");
            }
            return read(input);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read QuestsPlus runtime library list " + RESOURCE + ".", exception);
        }
    }

    public static List<String> read(InputStream input) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        }
    }
}
