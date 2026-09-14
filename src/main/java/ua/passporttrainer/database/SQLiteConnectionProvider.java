package ua.passporttrainer.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Creates JDBC connections without owning their lifecycle.
 */
public final class SQLiteConnectionProvider implements DatabaseConnectionProvider {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    private final String jdbcUrl;

    public SQLiteConnectionProvider(Path databasePath) {
        Path absolutePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
        jdbcUrl = JDBC_PREFIX + absolutePath;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
