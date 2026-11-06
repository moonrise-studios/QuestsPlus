package gg.moonrise.quests.loader;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimeLibrariesTest {

    @Test
    public void generatedListContainsEveryRuntimeLibraryWithAVersion() {
        List<String> coordinates = RuntimeLibraries.load();

        assertEquals(
                List.of(
                        "com.zaxxer:HikariCP",
                        "org.xerial:sqlite-jdbc",
                        "org.mariadb.jdbc:mariadb-java-client",
                        "com.mysql:mysql-connector-j",
                        "org.postgresql:postgresql",
                        "com.github.ben-manes.caffeine:caffeine",
                        "com.google.code.gson:gson"
                ),
                coordinates.stream().map(RuntimeLibrariesTest::moduleKey).toList()
        );
        for (String coordinate : coordinates) {
            String[] parts = coordinate.split(":");
            assertEquals(3, parts.length, coordinate);
            assertFalse(parts[2].isBlank(), coordinate);
        }
    }

    @Test
    public void readSkipsBlankLinesAndComments() throws IOException {
        String content = "# generated\n\n  com.google.code.gson:gson:2.13.2  \norg.xerial:sqlite-jdbc:3.50.3.0\n";

        List<String> coordinates = RuntimeLibraries.read(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        assertEquals(List.of("com.google.code.gson:gson:2.13.2", "org.xerial:sqlite-jdbc:3.50.3.0"), coordinates);
    }

    private static String moduleKey(String coordinate) {
        return coordinate.substring(0, coordinate.lastIndexOf(':'));
    }
}
