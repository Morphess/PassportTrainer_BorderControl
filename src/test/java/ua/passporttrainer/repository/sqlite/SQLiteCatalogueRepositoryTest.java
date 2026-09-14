package ua.passporttrainer.repository.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.CatalogueTestSupport;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.database.SQLiteConnectionProvider;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteCatalogueRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;
    private SQLiteConnectionProvider provider;
    private SQLiteCountryRepository countryRepository;
    private SQLitePassportRepository passportRepository;

    @BeforeEach
    void importCatalogue() throws Exception {
        databasePath = temporaryDirectory.resolve("repository.sqlite");
        provider = CatalogueTestSupport.importCatalogue(databasePath);
        countryRepository = new SQLiteCountryRepository(provider);
        passportRepository = new SQLitePassportRepository(provider);
    }

    @Test
    void countryRepositoryReadsCanonicalEntitiesByStableId() {
        List<Country> countries = countryRepository.findAll();
        assertEquals(139, countries.size());
        Country ukraine = countries.stream()
                .filter(country -> country.displayName().equals("Україна"))
                .findFirst()
                .orElseThrow();

        assertTrue(ukraine.id().startsWith("entity:"));
        assertEquals("continent:europe", ukraine.continentId());
        assertEquals(ukraine, countryRepository.findById(ukraine.id()).orElseThrow());
        assertTrue(countryRepository.findByIsoAlpha2("UA").isEmpty());
    }

    @Test
    void passportRepositoryMapsDocumentsAndImagesByStableIds() {
        Country ukraine = countryRepository.findAll().stream()
                .filter(country -> country.displayName().equals("Україна"))
                .findFirst()
                .orElseThrow();
        List<PassportDocument> documents = passportRepository.findByCountryId(ukraine.id());
        assertFalse(documents.isEmpty());

        PassportDocument document = documents.getFirst();
        assertTrue(document.id().startsWith("document:"));
        assertEquals(ukraine.id(), document.countryId());
        assertEquals(document, passportRepository.findById(document.id()).orElseThrow());

        List<PassportImage> images = passportRepository.findImagesByDocumentId(document.id());
        assertEquals(1, images.size());
        assertEquals(document.id(), images.getFirst().passportDocumentId());
        assertEquals(document.sourceRelativePath(), images.getFirst().relativePath());
    }

    @Test
    void repositoriesReturnEmptyResultsForMissingRecords() {
        assertTrue(countryRepository.findById("entity:missing").isEmpty());
        assertTrue(countryRepository.findByIsoAlpha2("ZZ").isEmpty());
        assertTrue(passportRepository.findById("document:missing").isEmpty());
        assertTrue(passportRepository.findByCountryId("entity:missing").isEmpty());
        assertTrue(passportRepository.findImagesByDocumentId("document:missing").isEmpty());
    }

    @Test
    void compositionRootExposesServicesOverReadOnlyCatalogue() throws Exception {
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(databasePath);
        assertEquals(139, context.countryService().findAll().size());
        Country first = context.countryService().findAll().getFirst();
        assertEquals(
                passportRepository.findByCountryId(first.id()),
                context.passportService().findByCountryId(first.id())
        );

        SQLiteConnectionProvider readOnly = SQLiteConnectionProvider.readOnly(databasePath);
        try (Connection connection = readOnly.getConnection();
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM documents"));
        }
    }
}
