package ua.passporttrainer.repository.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.database.SQLiteConnectionProvider;
import ua.passporttrainer.database.SQLiteMigrationRunner;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.QuizCandidate;
import ua.passporttrainer.model.ReviewStatus;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteQuizCandidateRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    private SQLiteConnectionProvider provider;
    private SQLitePassportRepository repository;

    @BeforeEach
    void createCatalogue() throws Exception {
        provider = new SQLiteConnectionProvider(temporaryDirectory.resolve("quiz-candidates.sqlite"));
        new SQLiteMigrationRunner().migrate(provider);
        repository = new SQLitePassportRepository(provider);
    }

    @Test
    void returnsOnlyCanonicalFullyVerifiedEligibleCandidates() throws Exception {
        insertContinent("continent:europe", 1);
        insertContinent("continent:asia", 2);
        insertEntity("entity:valid", ReviewStatus.VERIFIED);
        insertEntity("entity:unverified", ReviewStatus.AUTO_CLASSIFIED);
        insertMembership("entity:valid", "continent:europe", true, 0);
        insertMembership("entity:valid", "continent:asia", false, 1);
        insertMembership("entity:unverified", "continent:europe", true, 0);

        insertDocument(
                "document:valid", "entity:valid", DocumentType.ORDINARY_PASSPORT,
                ReviewStatus.VERIFIED, true, null
        );
        insertImage(
                "image:valid", "document:valid", ReviewStatus.VERIFIED,
                null, sha(1)
        );
        insertDocument(
                "document:ineligible", "entity:valid", DocumentType.ORDINARY_PASSPORT,
                ReviewStatus.VERIFIED, false, null
        );
        insertImage(
                "image:ineligible", "document:ineligible", ReviewStatus.VERIFIED,
                null, sha(2)
        );
        insertDocument(
                "document:unknown", "entity:valid", DocumentType.UNKNOWN,
                ReviewStatus.VERIFIED, true, null
        );
        insertImage(
                "image:unknown", "document:unknown", ReviewStatus.VERIFIED,
                null, sha(3)
        );
        insertDocument(
                "document:unverified", "entity:valid", DocumentType.ORDINARY_PASSPORT,
                ReviewStatus.NEEDS_REVIEW, true, null
        );
        insertImage(
                "image:unverified", "document:unverified", ReviewStatus.VERIFIED,
                null, sha(4)
        );
        insertDocument(
                "document:bad-image", "entity:valid", DocumentType.ORDINARY_PASSPORT,
                ReviewStatus.VERIFIED, true, null
        );
        insertImage(
                "image:bad-image", "document:bad-image", ReviewStatus.NEEDS_REVIEW,
                null, sha(5)
        );
        insertDocument(
                "document:linked", "entity:valid", DocumentType.ORDINARY_PASSPORT,
                ReviewStatus.VERIFIED, true, "document:valid"
        );
        insertImage(
                "image:linked-document", "document:linked", ReviewStatus.VERIFIED,
                null, sha(6)
        );
        insertDocument(
                "document:linked-image", "entity:valid", DocumentType.ORDINARY_PASSPORT,
                ReviewStatus.VERIFIED, true, null
        );
        insertImage(
                "image:linked", "document:linked-image", ReviewStatus.VERIFIED,
                "image:valid", sha(1)
        );
        insertDocument(
                "document:bad-entity", "entity:unverified", DocumentType.ORDINARY_PASSPORT,
                ReviewStatus.VERIFIED, true, null
        );
        insertImage(
                "image:bad-entity", "document:bad-entity", ReviewStatus.VERIFIED,
                null, sha(7)
        );

        List<QuizCandidate> candidates = repository.findQuizEligibleCandidates();

        assertEquals(1, candidates.size());
        QuizCandidate candidate = candidates.getFirst();
        assertAll(
                () -> assertEquals("document:valid", candidate.documentId()),
                () -> assertEquals("entity:valid", candidate.entityId()),
                () -> assertEquals("Valid", candidate.entityDisplayName()),
                () -> assertEquals(Set.of("continent:europe", "continent:asia"),
                        candidate.continentIds()),
                () -> assertEquals(DocumentType.ORDINARY_PASSPORT, candidate.documentType()),
                () -> assertEquals("image:valid", candidate.imageId()),
                () -> assertEquals("fixtures/valid.jpg", candidate.imageRelativePath()),
                () -> assertEquals(sha(1), candidate.imageSha256()),
                () -> assertEquals(ReviewStatus.VERIFIED, candidate.entityReviewStatus()),
                () -> assertEquals(ReviewStatus.VERIFIED, candidate.documentReviewStatus()),
                () -> assertEquals(ReviewStatus.VERIFIED, candidate.imageReviewStatus()),
                () -> assertEquals(candidate.documentId(), candidate.canonicalDocumentId()),
                () -> assertEquals(candidate.imageId(), candidate.canonicalImageId()),
                () -> assertTrue(candidate.quizEligible()),
                () -> assertTrue(candidate.usable())
        );
    }

    private void insertContinent(String id, int sortOrder) throws SQLException {
        try (Connection connection = provider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO continents(id, code, display_name_uk, source_name, sort_order) "
                             + "VALUES (?, ?, ?, ?, ?)"
             )) {
            statement.setString(1, id);
            statement.setString(2, id.substring(id.indexOf(':') + 1));
            statement.setString(3, id);
            statement.setString(4, id);
            statement.setInt(5, sortOrder);
            statement.executeUpdate();
        }
    }

    private void insertEntity(String id, ReviewStatus status) throws SQLException {
        try (Connection connection = provider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO entities(id, canonical_key, display_name_uk, entity_type, "
                             + "iso_alpha2, iso_alpha3, review_status, notes) "
                             + "VALUES (?, ?, ?, 'COUNTRY', NULL, NULL, ?, '')"
             )) {
            String code = id.substring(id.indexOf(':') + 1);
            statement.setString(1, id);
            statement.setString(2, code);
            statement.setString(3, Character.toUpperCase(code.charAt(0)) + code.substring(1));
            statement.setString(4, status.name());
            statement.executeUpdate();
        }
    }

    private void insertMembership(
            String entityId,
            String continentId,
            boolean primary,
            int sortOrder
    ) throws SQLException {
        try (Connection connection = provider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO entity_continents(entity_id, continent_id, is_primary, sort_order) "
                             + "VALUES (?, ?, ?, ?)"
             )) {
            statement.setString(1, entityId);
            statement.setString(2, continentId);
            statement.setInt(3, primary ? 1 : 0);
            statement.setInt(4, sortOrder);
            statement.executeUpdate();
        }
    }

    private void insertDocument(
            String id,
            String entityId,
            DocumentType documentType,
            ReviewStatus reviewStatus,
            boolean quizEligible,
            String duplicateOf
    ) throws SQLException {
        try (Connection connection = provider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO documents(id, entity_id, document_type, title_uk, variant, "
                             + "review_status, quiz_eligible, duplicate_of_document_id, notes) "
                             + "VALUES (?, ?, ?, ?, '', ?, ?, ?, '')"
             )) {
            statement.setString(1, id);
            statement.setString(2, entityId);
            statement.setString(3, documentType.name());
            statement.setString(4, "Fixture " + id);
            statement.setString(5, reviewStatus.name());
            statement.setInt(6, quizEligible ? 1 : 0);
            statement.setString(7, duplicateOf);
            statement.executeUpdate();
        }
    }

    private void insertImage(
            String id,
            String documentId,
            ReviewStatus reviewStatus,
            String duplicateOf,
            String sha256
    ) throws SQLException {
        try (Connection connection = provider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO passport_images(id, document_id, source_relative_path, "
                             + "source_filename, mime_type, format, width, height, file_size, sha256, "
                             + "review_status, duplicate_of_image_id, notes) "
                             + "VALUES (?, ?, ?, ?, 'image/jpeg', 'JPEG', 100, 60, 1000, ?, ?, ?, '')"
             )) {
            String suffix = id.substring(id.indexOf(':') + 1);
            statement.setString(1, id);
            statement.setString(2, documentId);
            statement.setString(3, "fixtures/" + suffix + ".jpg");
            statement.setString(4, suffix + ".jpg");
            statement.setString(5, sha256);
            statement.setString(6, reviewStatus.name());
            statement.setString(7, duplicateOf);
            statement.executeUpdate();
        }
    }

    private static String sha(int value) {
        return "%064x".formatted(value);
    }
}
