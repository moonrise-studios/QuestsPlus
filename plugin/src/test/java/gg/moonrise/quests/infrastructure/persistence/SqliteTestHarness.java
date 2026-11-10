package gg.moonrise.quests.infrastructure.persistence;

import java.nio.file.Path;

public final class SqliteTestHarness {

    private SqliteTestHarness() {
    }

    public static SqlProvider open(Path dataFolder) {
        return SqlTestHarness.openSqlite(dataFolder);
    }
}
