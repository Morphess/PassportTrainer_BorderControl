package ua.passporttrainer.repository.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.model.NewUser;
import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.repository.RepositoryAccessException;
import ua.passporttrainer.repository.UserRepository;

/** Persists local accounts in the writable application database. */
public final class SQLiteUserRepository implements UserRepository {

    private static final String COLUMNS = "id, username, display_name, password_hash, "
            + "role, active, created_at, updated_at";
    private static final String HAS_USERS = "SELECT EXISTS (SELECT 1 FROM users)";
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS + " FROM users WHERE id = ?";
    private static final String SELECT_BY_USERNAME = "SELECT " + COLUMNS
            + " FROM users WHERE username = ?";
    private static final String INSERT_USER = "INSERT INTO users ("
            + "username, display_name, password_hash, role, active, created_at, updated_at"
            + ") VALUES (?, ?, ?, ?, 1, ?, ?) RETURNING " + COLUMNS;
    private static final String INSERT_USER_IF_EMPTY = "INSERT INTO users ("
            + "username, display_name, password_hash, role, active, created_at, updated_at"
            + ") SELECT ?, ?, ?, ?, 1, ?, ? WHERE NOT EXISTS (SELECT 1 FROM users) "
            + "RETURNING " + COLUMNS;
    private static final String UPDATE_USER = "UPDATE users SET display_name = ?, "
            + "password_hash = ?, role = ?, active = ?, updated_at = ? "
            + "WHERE id = ? RETURNING " + COLUMNS;

    private final DatabaseConnectionProvider connectionProvider;

    public SQLiteUserRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public boolean hasUsers() {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(HAS_USERS);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) == 1;
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot check local users", exception);
        }
    }

    @Override
    public Optional<User> findById(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        return find(SELECT_BY_ID, statement -> statement.setLong(1, id), "ID");
    }

    @Override
    public Optional<User> findByUsername(String canonicalUsername) {
        Objects.requireNonNull(canonicalUsername, "canonicalUsername");
        if (canonicalUsername.isBlank()) {
            throw new IllegalArgumentException("canonicalUsername must not be blank");
        }
        return find(
                SELECT_BY_USERNAME,
                statement -> statement.setString(1, canonicalUsername),
                "username"
        );
    }

    @Override
    public User create(NewUser newUser) {
        Objects.requireNonNull(newUser, "newUser");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_USER)) {
            bindNewUser(statement, newUser);
            return requireReturnedUser(statement, "User insert returned no row");
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot create local user", exception);
        }
    }

    @Override
    public Optional<User> createIfEmpty(NewUser newUser) {
        Objects.requireNonNull(newUser, "newUser");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_USER_IF_EMPTY)) {
            bindNewUser(statement, newUser);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot create initial local user", exception);
        }
    }

    @Override
    public User update(User user) {
        Objects.requireNonNull(user, "user");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_USER)) {
            statement.setString(1, user.displayName());
            statement.setString(2, user.passwordHash());
            statement.setString(3, user.role().name());
            statement.setInt(4, user.active() ? 1 : 0);
            statement.setString(5, user.updatedAt().toString());
            statement.setLong(6, user.id());
            return requireReturnedUser(statement, "Unknown local user ID: " + user.id());
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot update local user", exception);
        }
    }

    private Optional<User> find(String sql, Binder binder, String keyDescription) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException(
                    "Cannot read local user by " + keyDescription, exception
            );
        }
    }

    private static void bindNewUser(PreparedStatement statement, NewUser newUser)
            throws SQLException {
        statement.setString(1, newUser.username());
        statement.setString(2, newUser.displayName());
        statement.setString(3, newUser.passwordHash());
        statement.setString(4, newUser.role().name());
        statement.setString(5, newUser.createdAt().toString());
        statement.setString(6, newUser.createdAt().toString());
    }

    private static User requireReturnedUser(PreparedStatement statement, String missingMessage)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException(missingMessage);
            }
            return map(resultSet);
        }
    }

    private static User map(ResultSet resultSet) throws SQLException {
        try {
            return new User(
                    resultSet.getLong("id"),
                    resultSet.getString("username"),
                    resultSet.getString("display_name"),
                    resultSet.getString("password_hash"),
                    UserRole.valueOf(resultSet.getString("role")),
                    resultSet.getInt("active") == 1,
                    Instant.parse(resultSet.getString("created_at")),
                    Instant.parse(resultSet.getString("updated_at"))
            );
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new SQLException("Invalid persisted local user", exception);
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
