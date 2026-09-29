package ua.passporttrainer.database;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.model.AssessmentProvenance;
import ua.passporttrainer.model.AssessmentSource;
import ua.passporttrainer.model.ExamResult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationDatabaseV3MigrationTest {

    private static final Path PASSPORT_CATALOGUE =
            Path.of("data/passport-catalogue/passport-catalogue.sqlite");
    private static final Instant STARTED = Instant.parse("2026-09-29T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-09-29T10:03:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void upgradesV2PreservesExistingFactsAndDoesNotTouchPassportCatalogue() throws Exception {
        byte[] catalogueHashBefore = sha256(PASSPORT_CATALOGUE);
        Path database = temporaryDirectory.resolve("upgrade-v2.sqlite");
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(database);
        createV2(provider);
        insertAdminAndResult(provider);

        ApplicationDatabaseMigrationRunner runner = new ApplicationDatabaseMigrationRunner();
        runner.migrate(provider);
        runner.migrate(provider);

        ExamResult migrated = ApplicationDatabaseContext.open(database)
                .resultRepository()
                .findById(10)
                .orElseThrow();
        assertEquals(
                new ExamResult(
                        10,
                        7L,
                        20,
                        AssessmentSource.AUTHORED,
                        Optional.of(new AssessmentProvenance("assessment:legacy", "version:1")),
                        STARTED,
                        COMPLETED,
                        7,
                        10
                ),
                migrated
        );
        assertEquals("admin", ApplicationDatabaseContext.open(database)
                .userRepository()
                .findById(7)
                .orElseThrow()
                .username());

        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(
                    3,
                    scalarInt(statement, "SELECT COUNT(*) FROM application_schema_migrations")
            );
            assertEquals(
                    5,
                    scalarInt(statement, "SELECT COUNT(*) FROM sqlite_schema "
                            + "WHERE type = 'table' AND name IN ("
                            + "'questions', 'question_versions', 'question_options', "
                            + "'media_metadata', 'assessments')")
            );
        }
        assertArrayEquals(catalogueHashBefore, sha256(PASSPORT_CATALOGUE));
    }

    @Test
    void rollsBackEveryV3ChangeWhenOneFoundationTableConflicts() throws Exception {
        Path database = temporaryDirectory.resolve("rollback-v3.sqlite");
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(database);
        createV2(provider);
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE question_versions (sentinel INTEGER) STRICT");
        }

        assertThrows(
                SQLException.class,
                () -> new ApplicationDatabaseMigrationRunner().migrate(provider)
        );

        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(
                    2,
                    scalarInt(statement, "SELECT MAX(version) FROM application_schema_migrations")
            );
            assertEquals(0, tableCount(statement, "questions"));
            assertEquals(0, tableCount(statement, "media_metadata"));
            assertEquals(0, tableCount(statement, "assessments"));
            assertEquals(1, tableCount(statement, "question_versions"));
        }
    }

    @Test
    void freshV3SchemaEnforcesReferencesStatesAndSingleCorrectOption() throws Exception {
        Path database = temporaryDirectory.resolve("v3-invariants.sqlite");
        ApplicationDatabaseContext.open(database);
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(database);

        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users ("
                    + "id, username, display_name, password_hash, role, active, created_at, updated_at"
                    + ") VALUES (7, 'admin', 'Administrator', 'hash', 'ADMIN', 1, "
                    + "'2026-09-29T10:00:00Z', '2026-09-29T10:00:00Z')");
            assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("INSERT INTO questions VALUES ("
                            + "'question:bad', 'UNKNOWN', 7, "
                            + "'2026-09-29T10:00:00Z', '2026-09-29T10:00:00Z')")
            );
            assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("INSERT INTO questions VALUES ("
                            + "'question:orphan', 'DRAFT', 999, "
                            + "'2026-09-29T10:00:00Z', '2026-09-29T10:00:00Z')")
            );
            statement.executeUpdate("INSERT INTO questions VALUES ("
                    + "'question:1', 'DRAFT', 7, "
                    + "'2026-09-29T10:00:00Z', '2026-09-29T10:00:00Z')");
            statement.executeUpdate("INSERT INTO question_versions ("
                    + "version_id, question_id, version_number, question_type, prompt, "
                    + "created_by, created_at) VALUES ("
                    + "'version:1', 'question:1', 1, 'THEORETICAL', 'Prompt', 7, "
                    + "'2026-09-29T10:00:00Z')");
            statement.executeUpdate("INSERT INTO question_options VALUES ("
                    + "'option:1', 'version:1', 0, 'Correct', 1)");
            statement.executeUpdate("INSERT INTO question_options VALUES ("
                    + "'option:2', 'version:1', 1, 'Wrong', 0)");
            statement.executeUpdate("INSERT INTO question_versions ("
                    + "version_id, question_id, version_number, question_type, prompt, "
                    + "created_by, created_at) VALUES ("
                    + "'version:2', 'question:1', 2, 'THEORETICAL', 'Updated prompt', 7, "
                    + "'2026-09-29T10:01:00Z')");
            statement.executeUpdate("INSERT INTO question_options VALUES ("
                    + "'option:1', 'version:2', 0, 'Correct', 1)");
            statement.executeUpdate("INSERT INTO question_options VALUES ("
                    + "'option:2', 'version:2', 1, 'Wrong', 0)");
            assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("INSERT INTO question_options VALUES ("
                            + "'option:3', 'version:1', 2, 'Also correct', 1)")
            );
        }
    }

    private static void createV2(SQLiteConnectionProvider provider) throws Exception {
        applyMigration(
                provider,
                1,
                "create_examination_results",
                "/db/application-migration/V001__create_examination_results.sql"
        );
        applyMigration(
                provider,
                2,
                "create_users_and_result_ownership",
                "/db/application-migration/V002__create_users_and_result_ownership.sql"
        );
    }

    private static void applyMigration(
            SQLiteConnectionProvider provider,
            int version,
            String name,
            String resource
    ) throws Exception {
        byte[] migrationBytes;
        try (InputStream input = ApplicationDatabaseV3MigrationTest.class
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing application migration: " + resource);
            }
            migrationBytes = input.readAllBytes();
        }
        String migrationSql = new String(migrationBytes, StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(migrationBytes)
        );

        try (Connection connection = provider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    for (String sql : migrationSql.split(";", -1)) {
                        if (!sql.isBlank()) {
                            statement.execute(sql.strip());
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO application_schema_migrations"
                                + "(version, name, checksum_sha256) VALUES (?, ?, ?)"
                )) {
                    statement.setInt(1, version);
                    statement.setString(2, name);
                    statement.setString(3, checksum);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void insertAdminAndResult(SQLiteConnectionProvider provider) throws SQLException {
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users ("
                    + "id, username, display_name, password_hash, role, active, created_at, updated_at"
                    + ") VALUES (7, 'admin', 'Administrator', 'hash', 'ADMIN', 1, "
                    + "'2026-09-29T10:00:00Z', '2026-09-29T10:00:00Z')");
            statement.executeUpdate("INSERT INTO examination_results ("
                    + "id, session_id, user_id, assessment_source, assessment_id, "
                    + "assessment_version, started_at, completed_at, correct_count, total_count"
                    + ") VALUES (10, 20, 7, 'AUTHORED', 'assessment:legacy', 'version:1', "
                    + "'2026-09-29T10:00:00Z', '2026-09-29T10:03:00Z', 7, 10)");
        }
    }

    private static int tableCount(Statement statement, String tableName) throws SQLException {
        try (PreparedStatement prepared = statement.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM sqlite_schema WHERE type = 'table' AND name = ?"
        )) {
            prepared.setString(1, tableName);
            try (ResultSet resultSet = prepared.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static int scalarInt(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static byte[] sha256(Path path) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
    }
}
