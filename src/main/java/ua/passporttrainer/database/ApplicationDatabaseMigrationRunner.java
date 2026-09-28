package ua.passporttrainer.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Applies the writable application database's ordered, checksummed migrations. */
public final class ApplicationDatabaseMigrationRunner {

    public static final int CURRENT_VERSION = 2;

    private static final String MIGRATION_TABLE = "application_schema_migrations";
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(
                    1,
                    "create_examination_results",
                    "/db/application-migration/V001__create_examination_results.sql"
            ),
            new Migration(
                    2,
                    "create_users_and_result_ownership",
                    "/db/application-migration/V002__create_users_and_result_ownership.sql"
            )
    );

    public void migrate(DatabaseConnectionProvider connectionProvider) throws SQLException {
        Objects.requireNonNull(connectionProvider, "connectionProvider");
        try (Connection connection = connectionProvider.getConnection()) {
            migrate(connection);
        }
    }

    public void migrate(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        if (!connection.getAutoCommit()) {
            throw new IllegalArgumentException(
                    "Migration connection must not already have an active transaction"
            );
        }

        List<LoadedMigration> migrations = loadMigrations();
        Map<Integer, AppliedMigration> applied = readAppliedMigrations(connection);
        validateAppliedMigrations(applied, migrations);

        connection.setAutoCommit(false);
        try {
            for (LoadedMigration migration : migrations) {
                if (!applied.containsKey(migration.version())) {
                    apply(connection, migration);
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            rollback(connection, exception);
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static Map<Integer, AppliedMigration> readAppliedMigrations(
            Connection connection
    ) throws SQLException {
        if (!tableExists(connection, MIGRATION_TABLE)) {
            return Map.of();
        }
        Map<Integer, AppliedMigration> applied = new TreeMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version, name, checksum_sha256 FROM "
                             + MIGRATION_TABLE + " ORDER BY version"
             )) {
            while (resultSet.next()) {
                int version = resultSet.getInt("version");
                AppliedMigration previous = applied.put(
                        version,
                        new AppliedMigration(
                                resultSet.getString("name"),
                                resultSet.getString("checksum_sha256")
                        )
                );
                if (previous != null) {
                    throw new SQLException("Duplicate applied migration version: " + version);
                }
            }
        }
        return Map.copyOf(applied);
    }

    private static boolean tableExists(Connection connection, String tableName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_schema WHERE type = 'table' AND name = ?"
        )) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void validateAppliedMigrations(
            Map<Integer, AppliedMigration> applied,
            List<LoadedMigration> migrations
    ) throws SQLException {
        Map<Integer, LoadedMigration> known = new TreeMap<>();
        for (LoadedMigration migration : migrations) {
            known.put(migration.version(), migration);
        }
        for (Map.Entry<Integer, AppliedMigration> entry : applied.entrySet()) {
            LoadedMigration migration = known.get(entry.getKey());
            if (migration == null) {
                throw new SQLException(
                        "Application database has unknown migration version: " + entry.getKey()
                );
            }
            AppliedMigration actual = entry.getValue();
            if (!actual.name().equals(migration.name())
                    || !actual.checksum().equals(migration.checksum())) {
                throw new SQLException(
                        "Applied application migration does not match resources: " + entry.getKey()
                );
            }
        }
    }

    private static void apply(Connection connection, LoadedMigration migration)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : migration.sql().split(";", -1)) {
                if (!sql.isBlank()) {
                    statement.execute(sql.strip());
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + MIGRATION_TABLE
                        + "(version, name, checksum_sha256) VALUES (?, ?, ?)"
        )) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.name());
            statement.setString(3, migration.checksum());
            statement.executeUpdate();
        }
    }

    private static List<LoadedMigration> loadMigrations() {
        return MIGRATIONS.stream().map(ApplicationDatabaseMigrationRunner::load).toList();
    }

    private static LoadedMigration load(Migration migration) {
        try (InputStream input = ApplicationDatabaseMigrationRunner.class.getResourceAsStream(
                migration.resource()
        )) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing application migration resource: " + migration.resource()
                );
            }
            byte[] bytes = input.readAllBytes();
            return new LoadedMigration(
                    migration.version(),
                    migration.name(),
                    new String(bytes, StandardCharsets.UTF_8),
                    sha256(bytes)
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot read application migration resource: " + migration.resource(),
                    exception
            );
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private record Migration(int version, String name, String resource) {
    }

    private record LoadedMigration(int version, String name, String sql, String checksum) {
    }

    private record AppliedMigration(String name, String checksum) {
    }
}
