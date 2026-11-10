package gg.moonrise.quests.core.sql;

import gg.moonrise.quests.core.sql.SqlProvider;
import java.nio.file.Path;

public final class SqliteTestHarness {

    private SqliteTestHarness() {
    }

    public static SqlProvider open(Path dataFolder) {
        return SqlTestHarness.openSqlite(dataFolder);
    }
}
