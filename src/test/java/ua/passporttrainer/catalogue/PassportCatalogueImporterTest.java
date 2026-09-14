package ua.passporttrainer.catalogue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.CatalogueTestSupport;
import ua.passporttrainer.database.SQLiteConnectionProvider;
import ua.passporttrainer.manifest.ManifestCatalogue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassportCatalogueImporterTest {

    private static final List<String> MANIFEST_FILES = List.of(
            "manifest.tsv", "continents.tsv", "entities.tsv", "documents.tsv", "images.tsv", "aliases.tsv"
    );

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;
    private SQLiteConnectionProvider provider;

    @BeforeEach
    void importFullCatalogue() throws Exception {
        databasePath = temporaryDirectory.resolve("catalogue.sqlite");
        provider = CatalogueTestSupport.importCatalogue(databasePath);
    }

    @Test
    void importsFullManifestWithDeclaredCounts() throws Exception {
        assertEquals(7, count("continents"));
        assertEquals(139, count("entities"));
        assertEquals(521, count("documents"));
        assertEquals(521, count("passport_images"));
        assertEquals(166, count("aliases"));
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT manifest_schema_version, source_root, continent_count, entity_count, "
                             + "document_count, image_count FROM catalogue_metadata"
             )) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
            assertEquals("Зразки паспортних документів", resultSet.getString(2));
            assertEquals(7, resultSet.getInt(3));
            assertEquals(139, resultSet.getInt(4));
            assertEquals(521, resultSet.getInt(5));
            assertEquals(521, resultSet.getInt(6));
        }
    }

    @Test
    void normalizesPrimaryAndAlternateContinentRelationships() throws Exception {
        assertEquals(142, count("entity_continents"));
        assertEquals(139, scalarInt("SELECT COUNT(*) FROM entity_continents WHERE is_primary = 1"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM entity_continents WHERE is_primary = 0"));
        assertEquals(1, scalarInt(
                "SELECT COUNT(*) FROM entity_continents ec JOIN entities e ON e.id = ec.entity_id "
                        + "WHERE e.display_name_uk = 'Туреччина' AND ec.continent_id = 'continent:europe' "
                        + "AND ec.is_primary = 0"
        ));
    }

    @Test
    void persistsEveryDocumentTypeWithoutReclassification() throws Exception {
        assertEquals(133, documentTypeCount("ORDINARY_PASSPORT"));
        assertEquals(162, documentTypeCount("DIPLOMATIC_PASSPORT"));
        assertEquals(79, documentTypeCount("SERVICE_PASSPORT"));
        assertEquals(146, documentTypeCount("NATIONAL_ID"));
        assertEquals(1, documentTypeCount("UNKNOWN"));
    }

    @Test
    void preservesReviewStatusAndQuizEligibility() throws Exception {
        assertEquals(462, reviewStatusCount("documents", "AUTO_CLASSIFIED"));
        assertEquals(59, reviewStatusCount("documents", "NEEDS_REVIEW"));
        assertEquals(462, reviewStatusCount("passport_images", "AUTO_CLASSIFIED"));
        assertEquals(59, reviewStatusCount("passport_images", "NEEDS_REVIEW"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM documents WHERE quiz_eligible = 1"));
    }

    @Test
    void preservesDuplicateRelationshipsWithoutChains() throws Exception {
        assertEquals(90, scalarInt(
                "SELECT COUNT(*) FROM documents WHERE duplicate_of_document_id IS NOT NULL"
        ));
        assertEquals(90, scalarInt(
                "SELECT COUNT(*) FROM passport_images WHERE duplicate_of_image_id IS NOT NULL"
        ));
        assertEquals(0, scalarInt(
                "SELECT COUNT(*) FROM documents d JOIN documents target "
                        + "ON target.id = d.duplicate_of_document_id "
                        + "WHERE target.duplicate_of_document_id IS NOT NULL"
        ));
        assertEquals(0, scalarInt(
                "SELECT COUNT(*) FROM passport_images i JOIN passport_images target "
                        + "ON target.id = i.duplicate_of_image_id "
                        + "WHERE target.duplicate_of_image_id IS NOT NULL"
        ));
    }

    @Test
    void preservesExactSourceRelativePath() throws Exception {
        ManifestCatalogue catalogue = ManifestCatalogue.read(CatalogueTestSupport.MANIFEST_DIRECTORY);
        ManifestCatalogue.ImageEntry expected = catalogue.images().get(100);
        try (Connection connection = provider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT source_relative_path FROM passport_images WHERE id = ?"
             )) {
            statement.setString(1, expected.id());
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(expected.sourcePath(), resultSet.getString(1));
            }
        }
    }

    @Test
    void rebuildIsLogicallyDeterministicAcrossExistingAndIndependentDatabases() throws Exception {
        String first = PassportCatalogueFingerprint.sha256(provider);
        new PassportCatalogueImporter(provider).rebuild(CatalogueTestSupport.MANIFEST_DIRECTORY);
        String rebuilt = PassportCatalogueFingerprint.sha256(provider);

        Path secondPath = temporaryDirectory.resolve("catalogue-second.sqlite");
        SQLiteConnectionProvider secondProvider = CatalogueTestSupport.importCatalogue(secondPath);
        String independent = PassportCatalogueFingerprint.sha256(secondProvider);

        assertEquals(first, rebuilt);
        assertEquals(first, independent);
    }

    @Test
    void rejectsInvalidManifestWithoutChangingExistingCatalogue() throws Exception {
        String before = PassportCatalogueFingerprint.sha256(provider);
        Path invalidManifest = copyManifest("invalid-manifest");
        List<String> documentLines = Files.readAllLines(
                invalidManifest.resolve("documents.tsv"), StandardCharsets.UTF_8
        );
        String[] fields = documentLines.get(1).split("\\t", -1);
        fields[1] = "entity:missing";
        documentLines.set(1, String.join("\t", fields));
        Files.write(invalidManifest.resolve("documents.tsv"), documentLines, StandardCharsets.UTF_8);

        assertThrows(
                IllegalArgumentException.class,
                () -> new PassportCatalogueImporter(provider).rebuild(invalidManifest)
        );
        assertEquals(before, PassportCatalogueFingerprint.sha256(provider));
    }

    @Test
    void rollsBackRebuildWhenDatabaseRejectsAnInsert() throws Exception {
        String before = PassportCatalogueFingerprint.sha256(provider);
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TRIGGER reject_document_insert BEFORE INSERT ON documents "
                            + "BEGIN SELECT RAISE(ABORT, 'forced test failure'); END"
            );
        }

        assertThrows(
                SQLException.class,
                () -> new PassportCatalogueImporter(provider).rebuild(CatalogueTestSupport.MANIFEST_DIRECTORY)
        );
        assertEquals(before, PassportCatalogueFingerprint.sha256(provider));
        assertEquals(521, count("documents"));
    }

    @Test
    void databaseValidatorDetectsCorruptedCatalogueMetadata() throws Exception {
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE catalogue_metadata SET document_count = 520");
            assertThrows(SQLException.class, () -> PassportCatalogueValidator.validate(connection));
        }
    }

    @Test
    void importedDatabasePassesFullIntegrityValidation() throws Exception {
        try (Connection connection = provider.getConnection()) {
            PassportCatalogueValidator.validate(connection);
        }
    }

    private Path copyManifest(String directoryName) throws Exception {
        Path destination = Files.createDirectory(temporaryDirectory.resolve(directoryName));
        for (String fileName : MANIFEST_FILES) {
            Files.copy(CatalogueTestSupport.MANIFEST_DIRECTORY.resolve(fileName), destination.resolve(fileName));
        }
        return destination;
    }

    private int documentTypeCount(String documentType) throws Exception {
        return scalarInt("SELECT COUNT(*) FROM documents WHERE document_type = '" + documentType + "'");
    }

    private int reviewStatusCount(String table, String reviewStatus) throws Exception {
        return scalarInt("SELECT COUNT(*) FROM " + table + " WHERE review_status = '" + reviewStatus + "'");
    }

    private int count(String table) throws Exception {
        return scalarInt("SELECT COUNT(*) FROM " + table);
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }
}
