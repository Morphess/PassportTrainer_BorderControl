package ua.passporttrainer.repository.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.model.AuthoredQuestionType;
import ua.passporttrainer.model.QuestionOption;
import ua.passporttrainer.model.QuestionVersion;
import ua.passporttrainer.repository.QuestionVersionRepository;
import ua.passporttrainer.repository.RepositoryAccessException;

/** Atomically appends and reconstructs immutable authored question versions. */
public final class SQLiteQuestionVersionRepository implements QuestionVersionRepository {

    private static final String INSERT_VERSION = "INSERT INTO question_versions ("
            + "version_id, question_id, version_number, question_type, prompt, instructions, "
            + "explanation, difficulty, created_by, created_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERT_OPTION = "INSERT INTO question_options ("
            + "option_id, version_id, display_order, option_text, correct"
            + ") VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_COLUMNS = "v.version_id, v.question_id, "
            + "v.version_number, v.question_type, v.prompt, v.instructions, v.explanation, "
            + "v.difficulty, v.created_by, v.created_at, o.option_id, o.display_order, "
            + "o.option_text, o.correct";
    private static final String SELECT_BY_ID = "SELECT " + SELECT_COLUMNS
            + " FROM question_versions v LEFT JOIN question_options o "
            + "ON o.version_id = v.version_id WHERE v.version_id = ? "
            + "ORDER BY o.display_order, o.option_id";
    private static final String SELECT_BY_QUESTION = "SELECT " + SELECT_COLUMNS
            + " FROM question_versions v LEFT JOIN question_options o "
            + "ON o.version_id = v.version_id WHERE v.question_id = ? "
            + "ORDER BY v.version_number, v.version_id, o.display_order, o.option_id";

    private final DatabaseConnectionProvider connectionProvider;

    public SQLiteQuestionVersionRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public QuestionVersion append(QuestionVersion version) {
        Objects.requireNonNull(version, "version");
        try (Connection connection = connectionProvider.getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new IllegalStateException("Question version connection already has a transaction");
            }
            connection.setAutoCommit(false);
            try {
                insertVersion(connection, version);
                insertOptions(connection, version);
                connection.commit();
                return version;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot append authored question version", exception);
        }
    }

    @Override
    public Optional<QuestionVersion> findById(String versionId) {
        requireId(versionId, "versionId");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setString(1, versionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<QuestionVersion> versions = mapVersions(resultSet);
                return versions.isEmpty() ? Optional.empty() : Optional.of(versions.getFirst());
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException(
                    "Cannot read authored question version by ID", exception
            );
        }
    }

    @Override
    public List<QuestionVersion> findByQuestionId(String questionId) {
        requireId(questionId, "questionId");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_QUESTION)) {
            statement.setString(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapVersions(resultSet);
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException(
                    "Cannot list authored question versions", exception
            );
        }
    }

    private static void insertVersion(Connection connection, QuestionVersion version)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_VERSION)) {
            statement.setString(1, version.versionId());
            statement.setString(2, version.questionId());
            statement.setInt(3, version.versionNumber());
            statement.setString(4, version.questionType().name());
            statement.setString(5, version.prompt());
            setOptionalText(statement, 6, version.instructions());
            setOptionalText(statement, 7, version.explanation());
            setOptionalText(statement, 8, version.difficulty());
            statement.setLong(9, version.createdBy());
            statement.setString(10, version.createdAt().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Question version insert affected an unexpected row count");
            }
        }
    }

    private static void insertOptions(Connection connection, QuestionVersion version)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_OPTION)) {
            for (QuestionOption option : version.options()) {
                statement.setString(1, option.optionId());
                statement.setString(2, version.versionId());
                statement.setInt(3, option.displayOrder());
                statement.setString(4, option.text());
                statement.setInt(5, option.correct() ? 1 : 0);
                statement.addBatch();
            }
            for (int result : statement.executeBatch()) {
                if (result == 0 || result == Statement.EXECUTE_FAILED) {
                    throw new SQLException("Question option insert failed");
                }
            }
        }
    }

    private static List<QuestionVersion> mapVersions(ResultSet resultSet) throws SQLException {
        try {
            List<QuestionVersion> versions = new ArrayList<>();
            VersionAccumulator current = null;
            while (resultSet.next()) {
                String versionId = resultSet.getString("version_id");
                if (current == null || !current.versionId.equals(versionId)) {
                    if (current != null) {
                        versions.add(current.build());
                    }
                    current = VersionAccumulator.from(resultSet);
                }
                String optionId = resultSet.getString("option_id");
                if (optionId != null) {
                    current.options.add(new QuestionOption(
                            optionId,
                            resultSet.getInt("display_order"),
                            resultSet.getString("option_text"),
                            resultSet.getInt("correct") == 1
                    ));
                }
            }
            if (current != null) {
                versions.add(current.build());
            }
            return List.copyOf(versions);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new SQLException("Invalid persisted authored question version", exception);
        }
    }

    private static void setOptionalText(
            PreparedStatement statement,
            int index,
            Optional<String> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.get());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static final class VersionAccumulator {

        private final String versionId;
        private final String questionId;
        private final int versionNumber;
        private final String questionType;
        private final String prompt;
        private final Optional<String> instructions;
        private final Optional<String> explanation;
        private final Optional<String> difficulty;
        private final long createdBy;
        private final String createdAt;
        private final List<QuestionOption> options = new ArrayList<>();

        private VersionAccumulator(
                String versionId,
                String questionId,
                int versionNumber,
                String questionType,
                String prompt,
                Optional<String> instructions,
                Optional<String> explanation,
                Optional<String> difficulty,
                long createdBy,
                String createdAt
        ) {
            this.versionId = versionId;
            this.questionId = questionId;
            this.versionNumber = versionNumber;
            this.questionType = questionType;
            this.prompt = prompt;
            this.instructions = instructions;
            this.explanation = explanation;
            this.difficulty = difficulty;
            this.createdBy = createdBy;
            this.createdAt = createdAt;
        }

        private static VersionAccumulator from(ResultSet resultSet) throws SQLException {
            return new VersionAccumulator(
                    resultSet.getString("version_id"),
                    resultSet.getString("question_id"),
                    resultSet.getInt("version_number"),
                    resultSet.getString("question_type"),
                    resultSet.getString("prompt"),
                    Optional.ofNullable(resultSet.getString("instructions")),
                    Optional.ofNullable(resultSet.getString("explanation")),
                    Optional.ofNullable(resultSet.getString("difficulty")),
                    resultSet.getLong("created_by"),
                    resultSet.getString("created_at")
            );
        }

        private QuestionVersion build() {
            return new QuestionVersion(
                    versionId,
                    questionId,
                    versionNumber,
                    AuthoredQuestionType.valueOf(questionType),
                    prompt,
                    instructions,
                    explanation,
                    difficulty,
                    options,
                    createdBy,
                    Instant.parse(createdAt)
            );
        }
    }
}
