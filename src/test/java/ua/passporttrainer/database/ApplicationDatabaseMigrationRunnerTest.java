package ua.passporttrainer.database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationDatabaseMigrationRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsFreshApplicationDatabaseAndAppliesSchemaExactlyOnce() throws Exception {
        Path database = temporaryDirectory.resolve("nested/application.sqlite");

        ApplicationDatabaseContext.open(database);
        ApplicationDatabaseContext.open(database);

        assertTrue(Files.isRegularFile(database));
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(database);
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(
                    ApplicationDatabaseMigrationRunner.CURRENT_VERSION,
                    scalarInt(statement, "SELECT MAX(version) FROM application_schema_migrations")
            );
            assertEquals(
                    ApplicationDatabaseMigrationRunner.CURRENT_VERSION,
                    scalarInt(statement, "SELECT COUNT(*) FROM application_schema_migrations")
            );
            assertEquals(
                    1,
                    scalarInt(statement, "SELECT COUNT(*) FROM sqlite_schema "
                            + "WHERE type = 'table' AND name = 'users'")
            );
            assertEquals(
                    1,
                    scalarInt(statement, "SELECT COUNT(*) FROM sqlite_schema "
                            + "WHERE type = 'table' AND name = 'examination_results'")
            );
            assertEquals(
                    5,
                    scalarInt(statement, "SELECT COUNT(*) FROM sqlite_schema "
                            + "WHERE type = 'table' AND name IN ("
                            + "'questions', 'question_versions', 'question_options', "
                            + "'media_metadata', 'assessments')")
            );
        }
    }

    @Test
    void rejectsUnknownNewerSchemaWithoutChangingIt() throws Exception {
        Path database = temporaryDirectory.resolve("newer.sqlite");
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(database);
        ApplicationDatabaseMigrationRunner runner = new ApplicationDatabaseMigrationRunner();
        runner.migrate(provider);
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO application_schema_migrations"
                            + "(version, name, checksum_sha256) VALUES "
                            + "(99, 'future', '"
                            + "0".repeat(64)
                            + "')"
            );
        }

        assertThrows(SQLException.class, () -> runner.migrate(provider));

        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(
                    ApplicationDatabaseMigrationRunner.CURRENT_VERSION + 1,
                    scalarInt(statement, "SELECT COUNT(*) FROM application_schema_migrations")
            );
            assertEquals(
                    0,
                    scalarInt(statement, "SELECT COUNT(*) FROM examination_results")
            );
        }
    }

    private static int scalarInt(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }
}
