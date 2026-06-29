package gg.moonrise.quests.core.service;

import java.nio.file.Path;

final class SqliteTestHarness {

    private SqliteTestHarness() {
    }

    static SqlProvider open(Path dataFolder) {
        return SqlTestHarness.openSqlite(dataFolder);
    }
}
