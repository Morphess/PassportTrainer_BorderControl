package ua.passporttrainer.database;

import org.sqlite.SQLiteConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/**
 * Creates JDBC connections without owning their lifecycle.
 */
public final class SQLiteConnectionProvider implements DatabaseConnectionProvider {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    private final String jdbcUrl;
    private final Properties connectionProperties;

    public SQLiteConnectionProvider(Path databasePath) {
        this(databasePath, false);
    }

    private SQLiteConnectionProvider(Path databasePath, boolean readOnly) {
        Path absolutePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
        jdbcUrl = JDBC_PREFIX + absolutePath;
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setReadOnly(readOnly);
        connectionProperties = config.toProperties();
    }

    public static SQLiteConnectionProvider readOnly(Path databasePath) {
        return new SQLiteConnectionProvider(databasePath, true);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, connectionProperties);
    }
}
