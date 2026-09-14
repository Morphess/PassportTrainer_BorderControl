package ua.passporttrainer.repository.sqlite;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.repository.CountryRepository;
import ua.passporttrainer.repository.RepositoryAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Reads canonical issuing entities from the SQLite reference catalogue. */
public final class SQLiteCountryRepository implements CountryRepository {

    private static final String SELECT_COUNTRY = "SELECT e.id, ec.continent_id, e.iso_alpha2, e.iso_alpha3, "
            + "e.display_name_uk, n.source_name FROM entities e "
            + "JOIN entity_continents ec ON ec.entity_id = e.id AND ec.is_primary = 1 "
            + "JOIN entity_source_names n ON n.entity_id = e.id AND n.sort_order = 0";

    private final DatabaseConnectionProvider connectionProvider;

    public SQLiteCountryRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public List<Country> findAll() {
        String sql = SELECT_COUNTRY + " ORDER BY e.canonical_key, e.id";
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<Country> countries = new ArrayList<>();
            while (resultSet.next()) {
                countries.add(mapCountry(resultSet));
            }
            return List.copyOf(countries);
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read catalogue entities", exception);
        }
    }

    @Override
    public Optional<Country> findById(String id) {
        Objects.requireNonNull(id, "id");
        return findOne(SELECT_COUNTRY + " WHERE e.id = ?", id, "ID");
    }

    @Override
    public Optional<Country> findByIsoAlpha2(String isoAlpha2) {
        Objects.requireNonNull(isoAlpha2, "isoAlpha2");
        String normalized = isoAlpha2.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return findOne(SELECT_COUNTRY + " WHERE e.iso_alpha2 = ?", normalized, "ISO alpha-2 code");
    }

    private Optional<Country> findOne(String sql, String value, String lookup) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Country country = mapCountry(resultSet);
                if (resultSet.next()) {
                    throw new RepositoryAccessException("Catalogue " + lookup + " is not unique: " + value, null);
                }
                return Optional.of(country);
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read catalogue entity by " + lookup, exception);
        }
    }

    private static Country mapCountry(ResultSet resultSet) throws SQLException {
        return new Country(
                resultSet.getString("id"),
                resultSet.getString("continent_id"),
                resultSet.getString("iso_alpha2"),
                resultSet.getString("iso_alpha3"),
                resultSet.getString("display_name_uk"),
                resultSet.getString("source_name")
        );
    }
}
