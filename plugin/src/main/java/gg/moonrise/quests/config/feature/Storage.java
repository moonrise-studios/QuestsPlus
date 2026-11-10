package gg.moonrise.quests.config.feature;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class Storage {
    @Comment({
            "",
            "SQL database to use for QuestsPlus data.",
            "Supported values: SQLITE, MARIADB, MYSQL, POSTGRESQL."
    })
    private StorageType type = StorageType.SQLITE;
    @Comment({
            "",
            "SQLite database file name stored inside the QuestsPlus plugin folder.",
            "Ignored when type is MARIADB, MYSQL, or POSTGRESQL."
    })
    private String databaseFile = "storage/quests.db";
    @Comment({
            "",
            "MariaDB connection settings used when type is MARIADB."
    })
    private MariaDb mariaDb = new MariaDb();
    @Comment({
            "",
            "MySQL connection settings used when type is MYSQL."
    })
    private MySql mysql = new MySql();
    @Comment({
            "",
            "PostgreSQL connection settings used when type is POSTGRESQL."
    })
    private PostgreSql postgresql = new PostgreSql();

    public enum StorageType {
        SQLITE,
        MARIADB,
        MYSQL,
        POSTGRESQL
    }

    @Getter
    @Configuration
    public static class MariaDb {
        private String connectionUrl = "jdbc:mariadb://127.0.0.1:3306/questsplus?characterEncoding=utf8";
        private String username = "questsplus";
        private String password = "change-me";
        private int poolSize = 10;
    }

    @Getter
    @Configuration
    public static class MySql {
        private String connectionUrl = "jdbc:mysql://127.0.0.1:3306/questsplus?characterEncoding=utf8";
        private String username = "questsplus";
        private String password = "change-me";
        private int poolSize = 10;
    }

    @Getter
    @Configuration
    public static class PostgreSql {
        private String connectionUrl = "jdbc:postgresql://127.0.0.1:5432/questsplus";
        private String username = "questsplus";
        private String password = "change-me";
        private int poolSize = 10;
    }
}
