package ua.passporttrainer.catalogue;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.database.SQLiteConnectionProvider;
import ua.passporttrainer.repository.ContinentRepository;
import ua.passporttrainer.repository.CountryRepository;
import ua.passporttrainer.repository.PassportRepository;
import ua.passporttrainer.repository.sqlite.SQLiteContinentRepository;
import ua.passporttrainer.repository.sqlite.SQLiteCountryRepository;
import ua.passporttrainer.repository.sqlite.SQLitePassportRepository;
import ua.passporttrainer.service.ContinentService;
import ua.passporttrainer.service.CountryService;
import ua.passporttrainer.service.PassportService;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Minimal non-UI composition root for read-only catalogue services. */
public record CatalogueApplicationContext(
        ContinentService continentService,
        CountryService countryService,
        PassportService passportService
) {

    public CatalogueApplicationContext {
        Objects.requireNonNull(continentService, "continentService");
        Objects.requireNonNull(countryService, "countryService");
        Objects.requireNonNull(passportService, "passportService");
    }

    public static CatalogueApplicationContext openReadOnly(Path cataloguePath) throws SQLException {
        return create(SQLiteConnectionProvider.readOnly(cataloguePath));
    }

    public static CatalogueApplicationContext create(
            DatabaseConnectionProvider connectionProvider
    ) throws SQLException {
        Objects.requireNonNull(connectionProvider, "connectionProvider");
        try (Connection connection = connectionProvider.getConnection()) {
            PassportCatalogueValidator.validate(connection);
        }
        ContinentRepository continentRepository = new SQLiteContinentRepository(connectionProvider);
        CountryRepository countryRepository = new SQLiteCountryRepository(connectionProvider);
        PassportRepository passportRepository = new SQLitePassportRepository(connectionProvider);
        return new CatalogueApplicationContext(
                new ContinentService(continentRepository),
                new CountryService(countryRepository),
                new PassportService(passportRepository)
        );
    }
}
