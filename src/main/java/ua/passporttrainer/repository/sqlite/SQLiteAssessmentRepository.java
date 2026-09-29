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
import ua.passporttrainer.model.AssessmentDefinition;
import ua.passporttrainer.model.AssessmentState;
import ua.passporttrainer.repository.AssessmentRepository;
import ua.passporttrainer.repository.RepositoryAccessException;

/** Persists the minimal authored assessment aggregate. */
public final class SQLiteAssessmentRepository implements AssessmentRepository {

    private static final String COLUMNS = "assessment_id, state, title, description, "
            + "created_by, created_at, updated_at";
    private static final String INSERT = "INSERT INTO assessments (" + COLUMNS
            + ") VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING " + COLUMNS;
    private static final String UPDATE = "UPDATE assessments SET state = ?, "
            + "title = ?, description = ?, updated_at = ? WHERE assessment_id = ? "
            + "RETURNING " + COLUMNS;
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS
            + " FROM assessments WHERE assessment_id = ?";
    private static final String SELECT_ALL = "SELECT " + COLUMNS
            + " FROM assessments ORDER BY created_at, assessment_id";

    private final DatabaseConnectionProvider connectionProvider;

    public SQLiteAssessmentRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public AssessmentDefinition create(AssessmentDefinition assessment) {
        Objects.requireNonNull(assessment, "assessment");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, assessment.assessmentId());
            statement.setString(2, assessment.state().name());
            statement.setString(3, assessment.title());
            setOptionalText(statement, 4, assessment.description());
            statement.setLong(5, assessment.createdBy());
            statement.setString(6, assessment.createdAt().toString());
            statement.setString(7, assessment.updatedAt().toString());
            return requireReturnedAssessment(
                    statement, "Assessment insert returned no row"
            );
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot create authored assessment", exception);
        }
    }

    @Override
    public AssessmentDefinition update(AssessmentDefinition assessment) {
        Objects.requireNonNull(assessment, "assessment");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, assessment.state().name());
            statement.setString(2, assessment.title());
            setOptionalText(statement, 3, assessment.description());
            statement.setString(4, assessment.updatedAt().toString());
            statement.setString(5, assessment.assessmentId());
            return requireReturnedAssessment(
                    statement, "Unknown assessment ID: " + assessment.assessmentId()
            );
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot update authored assessment", exception);
        }
    }

    @Override
    public Optional<AssessmentDefinition> findById(String assessmentId) {
        requireId(assessmentId, "assessmentId");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setString(1, assessmentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException(
                    "Cannot read authored assessment by ID", exception
            );
        }
    }

    @Override
    public List<AssessmentDefinition> findAll() {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            List<AssessmentDefinition> assessments = new ArrayList<>();
            while (resultSet.next()) {
                assessments.add(map(resultSet));
            }
            return List.copyOf(assessments);
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot list authored assessments", exception);
        }
    }

    private static AssessmentDefinition requireReturnedAssessment(
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

    private static AssessmentDefinition map(ResultSet resultSet) throws SQLException {
        try {
            return new AssessmentDefinition(
                    resultSet.getString("assessment_id"),
                    AssessmentState.valueOf(resultSet.getString("state")),
                    resultSet.getString("title"),
                    Optional.ofNullable(resultSet.getString("description")),
                    resultSet.getLong("created_by"),
                    Instant.parse(resultSet.getString("created_at")),
                    Instant.parse(resultSet.getString("updated_at"))
            );
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new SQLException("Invalid persisted authored assessment", exception);
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
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static void requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
