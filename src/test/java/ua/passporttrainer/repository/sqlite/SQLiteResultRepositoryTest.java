package ua.passporttrainer.repository.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.database.ApplicationDatabaseContext;
import ua.passporttrainer.database.SQLiteConnectionProvider;
import ua.passporttrainer.model.AssessmentProvenance;
import ua.passporttrainer.model.AssessmentSource;
import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.model.NewUser;
import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.repository.RepositoryAccessException;
import ua.passporttrainer.repository.ResultRepository;
import ua.passporttrainer.service.StatisticsService;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteResultRepositoryTest {

    private static final Instant STARTED = Instant.parse("2026-09-24T12:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-09-24T12:03:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsAnonymousGeneratedResultAndStoresNullUser() throws Exception {
        Path database = temporaryDirectory.resolve("anonymous.sqlite");
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(database);
        ExamResult expected = result(
                10, null, 100, AssessmentSource.GENERATED, Optional.empty(), 7
        );

        context.resultRepository().save(expected);
        ExamResult read = context.resultRepository().findById(expected.id()).orElseThrow();

        assertAll(
                () -> assertEquals(expected, read),
                () -> assertEquals(3, read.incorrectAnswers()),
                () -> assertEquals(70.0, read.scorePercent()),
                () -> assertEquals(180, read.durationSeconds())
        );
        try (Connection connection = new SQLiteConnectionProvider(database).getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT user_id, assessment_source FROM examination_results"
             )) {
            assertTrue(resultSet.next());
            assertNull(resultSet.getObject("user_id"));
            assertEquals("GENERATED", resultSet.getString("assessment_source"));
        }
    }

    @Test
    void roundTripsOwnedAuthoredProvenanceAndReadsHistoryAfterDeactivation()
            throws Exception {
        Path database = temporaryDirectory.resolve("authored.sqlite");
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(database);
        User owner = context.userRepository().create(new NewUser(
                "owner", "Result Owner", "encoded-test-hash", UserRole.USER, STARTED
        ));
        AssessmentProvenance provenance = new AssessmentProvenance(
                "test:border-basics", "version:2026-09"
        );
        ExamResult older = result(
                11, owner.id(), 101, AssessmentSource.AUTHORED,
                Optional.of(provenance), 6
        );
        ExamResult newer = new ExamResult(
                12,
                owner.id(),
                102,
                AssessmentSource.AUTHORED,
                Optional.of(provenance),
                STARTED.plusSeconds(600),
                COMPLETED.plusSeconds(600),
                8,
                10
        );
        context.resultRepository().save(older);
        context.resultRepository().save(newer);
        User deactivated = context.userRepository().update(new User(
                owner.id(),
                owner.username(),
                owner.displayName(),
                owner.passwordHash(),
                owner.role(),
                false,
                owner.createdAt(),
                COMPLETED.plusSeconds(1_200)
        ));

        List<ExamResult> results = context.statisticsService().findResults(owner.id());
        StatisticsService separatelyComposed = new StatisticsService(context.resultRepository());

        assertAll(
                () -> assertFalse(deactivated.active()),
                () -> assertEquals(List.of(newer, older), results),
                () -> assertEquals(owner.id(), results.getFirst().userId()),
                () -> assertEquals(
                        Optional.of(provenance),
                        results.getFirst().assessmentProvenance()
                ),
                () -> assertEquals(
                        70.0,
                        separatelyComposed.averageScorePercent(owner.id()).orElseThrow()
                )
        );
        try (Connection connection = new SQLiteConnectionProvider(database).getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM users WHERE id = ?"
             )) {
            statement.setLong(1, owner.id());
            assertThrows(SQLException.class, statement::executeUpdate);
        }
        assertEquals(
                List.of(newer, older),
                context.resultRepository().findByUserId(owner.id())
        );
    }

    @Test
    void duplicateSessionSaveIsIdempotentButConflictingFactsAreRejected() throws Exception {
        Path database = temporaryDirectory.resolve("idempotent.sqlite");
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(database);
        ExamResult original = result(
                20, null, 200, AssessmentSource.GENERATED, Optional.empty(), 9
        );
        ExamResult retryWithDifferentResultId = result(
                21, null, 200, AssessmentSource.GENERATED, Optional.empty(), 9
        );
        ExamResult conflicting = result(
                22, null, 200, AssessmentSource.GENERATED, Optional.empty(), 8
        );

        context.resultRepository().save(original);
        ExamResult retry = context.resultRepository().save(retryWithDifferentResultId);

        assertEquals(original, retry);
        assertThrows(
                RepositoryAccessException.class,
                () -> context.resultRepository().save(conflicting)
        );
        try (Connection connection = new SQLiteConnectionProvider(database).getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM examination_results WHERE session_id = 200"
             )) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    void retryAfterAmbiguousPostCommitFailureDoesNotDuplicateSession() throws Exception {
        Path database = temporaryDirectory.resolve("retry.sqlite");
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(database);
        ResultRepository delegate = context.resultRepository();
        ResultRepository failAfterFirstCommit = new ResultRepository() {
            private boolean fail = true;

            @Override
            public ExamResult save(ExamResult result) {
                ExamResult persisted = delegate.save(result);
                if (fail) {
                    fail = false;
                    throw new RepositoryAccessException("Simulated failure after commit");
                }
                return persisted;
            }

            @Override
            public Optional<ExamResult> findById(long id) {
                return delegate.findById(id);
            }

            @Override
            public List<ExamResult> findByUserId(long userId) {
                return delegate.findByUserId(userId);
            }
        };
        ExamResult expected = result(
                30, null, 300, AssessmentSource.GENERATED, Optional.empty(), 10
        );

        assertThrows(RepositoryAccessException.class, () -> failAfterFirstCommit.save(expected));
        ExamResult retried = failAfterFirstCommit.save(expected);

        assertEquals(expected, retried);
        try (Connection connection = new SQLiteConnectionProvider(database).getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM examination_results WHERE session_id = 300"
             )) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    private static ExamResult result(
            long id,
            Long userId,
            long sessionId,
            AssessmentSource source,
            Optional<AssessmentProvenance> provenance,
            int correct
    ) {
        return new ExamResult(
                id,
                userId,
                sessionId,
                source,
                provenance,
                STARTED,
                COMPLETED,
                correct,
                10
        );
    }
}
