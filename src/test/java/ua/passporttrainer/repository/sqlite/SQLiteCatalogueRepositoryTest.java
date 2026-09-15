package ua.passporttrainer.repository.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.CatalogueTestSupport;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.database.SQLiteConnectionProvider;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.EntityType;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.model.ReviewStatus;

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
        assertEquals(EntityType.COUNTRY, ukraine.entityType());
        assertEquals(ReviewStatus.AUTO_CLASSIFIED, ukraine.reviewStatus());
        assertEquals(passportRepository.findByCountryId(ukraine.id()).size(), ukraine.documentCount());
        assertEquals(ukraine, countryRepository.findById(ukraine.id()).orElseThrow());
        assertTrue(countryRepository.findByIsoAlpha2("UA").isEmpty());
    }

    @Test
    void continentAndCountryServicesIncludeAlternateGeography() throws Exception {
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(databasePath);
        assertEquals(7, context.continentService().findAll().size());
        assertEquals("continent:europe", context.continentService().findAll().getFirst().id());

        List<Country> europe = context.countryService().findByContinentId("continent:europe");
        List<Country> asia = context.countryService().findByContinentId("continent:asia");
        assertTrue(europe.stream().anyMatch(country -> country.displayName().equals("Туреччина")));
        assertTrue(asia.stream().anyMatch(country -> country.displayName().equals("Туреччина")));
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
        assertEquals(ReviewStatus.AUTO_CLASSIFIED, document.reviewStatus());
        assertEquals(1, document.imageCount());
        assertEquals(document, passportRepository.findById(document.id()).orElseThrow());

        List<PassportImage> images = passportRepository.findImagesByDocumentId(document.id());
        assertEquals(1, images.size());
        assertEquals(document.id(), images.getFirst().passportDocumentId());
        assertEquals(document.sourceRelativePath(), images.getFirst().relativePath());
    }

    @Test
    void passportRepositoryPreservesDistinctRecordsWithTheSamePresentationTitle() {
        Country brunei = countryRepository.findAll().stream()
                .filter(country -> country.displayName().equals("Бруней"))
                .findFirst()
                .orElseThrow();

        long ordinaryPassports = passportRepository.findByCountryId(brunei.id()).stream()
                .filter(document -> document.documentType() == DocumentType.ORDINARY_PASSPORT)
                .filter(document -> document.title().equals("Звичайний паспорт"))
                .count();

        assertEquals(3, ordinaryPassports);
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
        assertEquals(7, context.continentService().findAll().size());
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
