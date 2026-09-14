package ua.passporttrainer.repository.sqlite;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.model.Continent;
import ua.passporttrainer.repository.ContinentRepository;
import ua.passporttrainer.repository.RepositoryAccessException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads canonical continents from the SQLite reference catalogue. */
public final class SQLiteContinentRepository implements ContinentRepository {

    private static final String SELECT_CONTINENT =
            "SELECT id, code, display_name_uk, source_name, sort_order FROM continents";

    private final DatabaseConnectionProvider connectionProvider;

    public SQLiteContinentRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public List<Continent> findAll() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_CONTINENT + " ORDER BY sort_order, id")) {
            List<Continent> continents = new ArrayList<>();
            while (resultSet.next()) {
                continents.add(mapContinent(resultSet));
            }
            return List.copyOf(continents);
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read catalogue continents", exception);
        }
    }

    private static Continent mapContinent(ResultSet resultSet) throws SQLException {
        return new Continent(
                resultSet.getString("id"),
                resultSet.getString("code"),
                resultSet.getString("display_name_uk"),
                resultSet.getString("source_name"),
                resultSet.getInt("sort_order")
        );
    }
}
