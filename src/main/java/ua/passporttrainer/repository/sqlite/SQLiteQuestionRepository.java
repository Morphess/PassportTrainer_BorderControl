package ua.passporttrainer.repository.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.model.Question;
import ua.passporttrainer.model.QuestionLifecycleState;
import ua.passporttrainer.repository.QuestionRepository;
import ua.passporttrainer.repository.RepositoryAccessException;

/** Persists authored question aggregate identity and lifecycle state. */
public final class SQLiteQuestionRepository implements QuestionRepository {

    private static final String COLUMNS = "question_id, lifecycle_state, created_by, "
            + "created_at, updated_at";
    private static final String INSERT = "INSERT INTO questions (" + COLUMNS
            + ") VALUES (?, ?, ?, ?, ?) RETURNING " + COLUMNS;
    private static final String UPDATE = "UPDATE questions SET lifecycle_state = ?, "
            + "updated_at = ? WHERE question_id = ? RETURNING " + COLUMNS;
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS
            + " FROM questions WHERE question_id = ?";
    private static final String SELECT_ALL = "SELECT " + COLUMNS
            + " FROM questions ORDER BY created_at, question_id";

    private final DatabaseConnectionProvider connectionProvider;

    public SQLiteQuestionRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public Question create(Question question) {
        Objects.requireNonNull(question, "question");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, question.questionId());
            statement.setString(2, question.lifecycleState().name());
            statement.setLong(3, question.createdBy());
            statement.setString(4, question.createdAt().toString());
            statement.setString(5, question.updatedAt().toString());
            return requireReturnedQuestion(statement, "Question insert returned no row");
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot create authored question", exception);
        }
    }

    @Override
    public Question update(Question question) {
        Objects.requireNonNull(question, "question");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, question.lifecycleState().name());
            statement.setString(2, question.updatedAt().toString());
            statement.setString(3, question.questionId());
            return requireReturnedQuestion(
                    statement, "Unknown authored question ID: " + question.questionId()
            );
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot update authored question", exception);
        }
    }

    @Override
    public Optional<Question> findById(String questionId) {
        requireId(questionId, "questionId");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setString(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read authored question by ID", exception);
        }
    }

    @Override
    public List<Question> findAll() {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            List<Question> questions = new ArrayList<>();
            while (resultSet.next()) {
                questions.add(map(resultSet));
            }
            return List.copyOf(questions);
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot list authored questions", exception);
        }
    }

    private static Question requireReturnedQuestion(
            PreparedStatement statement,
            String missingMessage
    ) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException(missingMessage);
            }
            return map(resultSet);
        }
    }

    private static Question map(ResultSet resultSet) throws SQLException {
        try {
            return new Question(
                    resultSet.getString("question_id"),
                    QuestionLifecycleState.valueOf(resultSet.getString("lifecycle_state")),
                    resultSet.getLong("created_by"),
                    Instant.parse(resultSet.getString("created_at")),
                    Instant.parse(resultSet.getString("updated_at"))
            );
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new SQLException("Invalid persisted authored question", exception);
        }
    }

    private static void requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
