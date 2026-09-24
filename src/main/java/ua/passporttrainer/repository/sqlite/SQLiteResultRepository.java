package ua.passporttrainer.repository.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.model.AssessmentProvenance;
import ua.passporttrainer.model.AssessmentSource;
import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.repository.RepositoryAccessException;
import ua.passporttrainer.repository.ResultRepository;

/** Persists completed Examination summaries in the writable application database. */
public final class SQLiteResultRepository implements ResultRepository {

    private static final String COLUMNS = "id, session_id, user_id, assessment_source, "
            + "assessment_id, assessment_version, started_at, completed_at, "
            + "correct_count, total_count";
    private static final String INSERT_RESULT = "INSERT INTO examination_results ("
            + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(session_id) DO NOTHING";
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS
            + " FROM examination_results WHERE id = ?";
    private static final String SELECT_BY_SESSION = "SELECT " + COLUMNS
            + " FROM examination_results WHERE session_id = ?";
    private static final String SELECT_BY_USER = "SELECT " + COLUMNS
            + " FROM examination_results WHERE user_id = ? "
            + "ORDER BY completed_at DESC, id DESC";

    private final DatabaseConnectionProvider connectionProvider;

    public SQLiteResultRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public ExamResult save(ExamResult result) {
        Objects.requireNonNull(result, "result");
        try (Connection connection = connectionProvider.getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new IllegalStateException("Result connection already has an active transaction");
            }
            connection.setAutoCommit(false);
            try {
                int inserted = insert(connection, result);
                ExamResult persisted = inserted == 1
                        ? result
                        : findBySessionId(connection, result.quizSessionId()).orElseThrow(() ->
                                new RepositoryAccessException(
                                        "Session result conflict did not identify an existing row"
                                )
                        );
                if (inserted == 0 && !sameSessionFacts(persisted, result)) {
                    throw new RepositoryAccessException(
                            "Examination session already has a different persisted result"
                    );
                }
                connection.commit();
                return persisted;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot save Examination result", exception);
        }
    }

    @Override
    public Optional<ExamResult> findById(long id) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setLong(1, id);
            return findOne(statement);
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read Examination result by ID", exception);
        }
    }

    @Override
    public List<ExamResult> findByUserId(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_USER)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ExamResult> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(map(resultSet));
                }
                return List.copyOf(results);
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read Examination results by user", exception);
        }
    }

    private static int insert(Connection connection, ExamResult result) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RESULT)) {
            statement.setLong(1, result.id());
            statement.setLong(2, result.quizSessionId());
            if (result.userId() == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, result.userId());
            }
            statement.setString(4, result.assessmentSource().name());
            AssessmentProvenance provenance = result.assessmentProvenance().orElse(null);
            if (provenance == null) {
                statement.setNull(5, Types.VARCHAR);
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(5, provenance.assessmentId());
                statement.setString(6, provenance.versionId());
            }
            statement.setString(7, result.startedAt().toString());
            statement.setString(8, result.completedAt().toString());
            statement.setInt(9, result.correctAnswers());
            statement.setInt(10, result.totalQuestions());
            return statement.executeUpdate();
        }
    }

    private static Optional<ExamResult> findBySessionId(Connection connection, long sessionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_SESSION)) {
            statement.setLong(1, sessionId);
            return findOne(statement);
        }
    }

    private static Optional<ExamResult> findOne(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
        }
    }

    private static ExamResult map(ResultSet resultSet) throws SQLException {
        long userIdValue = resultSet.getLong("user_id");
        Long userId = resultSet.wasNull() ? null : userIdValue;
        String assessmentId = resultSet.getString("assessment_id");
        String assessmentVersion = resultSet.getString("assessment_version");
        if ((assessmentId == null) != (assessmentVersion == null)) {
            throw new SQLException("Incomplete persisted assessment provenance");
        }
        Optional<AssessmentProvenance> provenance = assessmentId == null
                ? Optional.empty()
                : Optional.of(new AssessmentProvenance(assessmentId, assessmentVersion));
        try {
            return new ExamResult(
                    resultSet.getLong("id"),
                    userId,
                    resultSet.getLong("session_id"),
                    AssessmentSource.valueOf(resultSet.getString("assessment_source")),
                    provenance,
                    Instant.parse(resultSet.getString("started_at")),
                    Instant.parse(resultSet.getString("completed_at")),
                    resultSet.getInt("correct_count"),
                    resultSet.getInt("total_count")
            );
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new SQLException("Invalid persisted Examination result", exception);
        }
    }

    private static boolean sameSessionFacts(ExamResult first, ExamResult second) {
        boolean sameUser = first.userId() == null
                ? second.userId() == null
                : first.userId().equals(second.userId());
        return sameUser
                && first.quizSessionId() == second.quizSessionId()
                && first.assessmentSource() == second.assessmentSource()
                && first.assessmentProvenance().equals(second.assessmentProvenance())
                && first.startedAt().equals(second.startedAt())
                && first.completedAt().equals(second.completedAt())
                && first.correctAnswers() == second.correctAnswers()
                && first.totalQuestions() == second.totalQuestions();
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
