package ua.passporttrainer.database;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationDatabaseV2MigrationTest {

    private static final String V1_RESOURCE =
            "/db/application-migration/V001__create_examination_results.sql";
    private static final Instant STARTED = Instant.parse("2026-09-24T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-09-24T10:03:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void upgradesV1AndPreservesHistoricalAnonymousResultExactly() throws Exception {
        Path database = temporaryDirectory.resolve("upgrade.sqlite");
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(database);
        createV1(provider);
        insertV1Result(provider, null, AssessmentSource.AUTHORED, "assessment:legacy", "v1");
        ExamResult expected = new ExamResult(
                10,
                null,
                20,
                AssessmentSource.AUTHORED,
                Optional.of(new AssessmentProvenance("assessment:legacy", "v1")),
                STARTED,
                COMPLETED,
                7,
                10
        );

        new ApplicationDatabaseMigrationRunner().migrate(provider);
        ExamResult migrated = ApplicationDatabaseContext.open(database)
                .resultRepository()
                .findById(expected.id())
                .orElseThrow();

        assertEquals(expected, migrated);
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(
                    ApplicationDatabaseMigrationRunner.CURRENT_VERSION,
                    scalarInt(statement, "SELECT MAX(version) FROM application_schema_migrations")
            );
            assertEquals(
                    1,
                    scalarInt(statement, "SELECT COUNT(*) FROM sqlite_schema "
                            + "WHERE type = 'index' "
                            + "AND name = 'examination_results_user_completed_idx'")
            );
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT user_id FROM examination_results WHERE id = 10"
            )) {
                resultSet.next();
                assertNull(resultSet.getObject("user_id"));
            }
        }
    }

    @Test
    void rejectsOrphanOwnerAndRollsBackEveryV2SchemaChange() throws Exception {
        Path database = temporaryDirectory.resolve("orphan.sqlite");
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(database);
        createV1(provider);
        insertV1Result(provider, 99L, AssessmentSource.GENERATED, null, null);

        assertThrows(
                SQLException.class,
                () -> new ApplicationDatabaseMigrationRunner().migrate(provider)
        );

        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(
                    1,
                    scalarInt(statement, "SELECT MAX(version) FROM application_schema_migrations")
            );
            assertEquals(
                    0,
                    scalarInt(statement, "SELECT COUNT(*) FROM sqlite_schema "
                            + "WHERE type = 'table' AND name = 'users'")
            );
            assertEquals(
                    99,
                    scalarInt(statement, "SELECT user_id FROM examination_results WHERE id = 10")
            );
        }
    }

    @Test
    void foreignKeyRejectsUnknownNonNullResultOwner() throws Exception {
        Path database = temporaryDirectory.resolve("foreign-key.sqlite");
        ApplicationDatabaseContext.open(database);
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(database);

        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("INSERT INTO examination_results ("
                            + "id, session_id, user_id, assessment_source, "
                            + "started_at, completed_at, correct_count, total_count"
                            + ") VALUES (1, 1, 999, 'GENERATED', "
                            + "'2026-09-24T10:00:00Z', '2026-09-24T10:03:00Z', 7, 10)")
            );
        }
    }

    private static void createV1(SQLiteConnectionProvider provider) throws Exception {
        byte[] migrationBytes;
        try (InputStream input = ApplicationDatabaseV2MigrationTest.class
                .getResourceAsStream(V1_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing V1 application migration");
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
                                + "(version, name, checksum_sha256) VALUES (1, ?, ?)"
                )) {
                    statement.setString(1, "create_examination_results");
                    statement.setString(2, checksum);
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

    private static void insertV1Result(
            SQLiteConnectionProvider provider,
            Long userId,
            AssessmentSource source,
            String assessmentId,
            String assessmentVersion
    ) throws SQLException {
        String sql = "INSERT INTO examination_results ("
                + "id, session_id, user_id, assessment_source, assessment_id, "
                + "assessment_version, started_at, completed_at, correct_count, total_count"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = provider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, 10);
            statement.setLong(2, 20);
            if (userId == null) {
                statement.setObject(3, null);
            } else {
                statement.setLong(3, userId);
            }
            statement.setString(4, source.name());
            statement.setString(5, assessmentId);
            statement.setString(6, assessmentVersion);
            statement.setString(7, STARTED.toString());
            statement.setString(8, COMPLETED.toString());
            statement.setInt(9, 7);
            statement.setInt(10, 10);
            statement.executeUpdate();
        }
    }

    private static int scalarInt(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }
}
