package gg.moonrise.quests.core.service;

import java.nio.file.Path;

final class SqliteTestHarness {

    private SqliteTestHarness() {
    }

    public static SqlProvider open(Path dataFolder) {
        return SqlTestHarness.openSqlite(dataFolder);
    }
}
