package gg.moonrise.quests.core.sql;

import com.zaxxer.hikari.HikariConfig;
import gg.moonrise.quests.config.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * Connection settings of a networked database (MySQL, MariaDB or PostgreSQL).
 */
public record NetworkSettings(String connectionUrl, String username, String password, int poolSize) {

    public static NetworkSettings of(@NotNull Storage.MySql settings) {
        return new NetworkSettings(settings.getConnectionUrl(), settings.getUsername(), settings.getPassword(), settings.getPoolSize());
    }

    public static NetworkSettings of(@NotNull Storage.MariaDb settings) {
        return new NetworkSettings(settings.getConnectionUrl(), settings.getUsername(), settings.getPassword(), settings.getPoolSize());
    }

    public static NetworkSettings of(@NotNull Storage.PostgreSql settings) {
        return new NetworkSettings(settings.getConnectionUrl(), settings.getUsername(), settings.getPassword(), settings.getPoolSize());
    }

    public HikariConfig hikariConfig(@NotNull String poolName) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(connectionUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(Math.max(1, poolSize));
        hikariConfig.setPoolName(poolName);
        return hikariConfig;
    }
}
