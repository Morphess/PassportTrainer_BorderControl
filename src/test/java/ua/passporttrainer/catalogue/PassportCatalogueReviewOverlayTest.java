package ua.passporttrainer.catalogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.database.SQLiteConnectionProvider;
import ua.passporttrainer.manifest.ManifestCatalogue;
import ua.passporttrainer.manifest.SourceNormalizer;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.QuizGenerationFailureReason;
import ua.passporttrainer.model.QuizGenerationRequest;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.repository.sqlite.SQLitePassportRepository;
import ua.passporttrainer.service.QuizGenerationException;
import ua.passporttrainer.service.QuizGenerationService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassportCatalogueReviewOverlayTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void explicitReviewAndActivationMaterializeWithoutChangingStageNineApi() throws Exception {
        Fixture fixture = createFixture("active");
        Path databasePath = temporaryDirectory.resolve("active.sqlite");
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(databasePath);
        new PassportCatalogueImporter(provider).rebuild(fixture.manifestDirectory(), fixture.reviewSource());

        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals("AUTO_CLASSIFIED", text(statement,
                    "SELECT manifest_review_status FROM entities"));
            assertEquals("VERIFIED", text(statement, "SELECT review_status FROM entities"));
            assertEquals("AUTO_CLASSIFIED", text(statement,
                    "SELECT manifest_review_status FROM documents"));
            assertEquals("VERIFIED", text(statement, "SELECT review_status FROM documents"));
            assertEquals(0, integer(statement, "SELECT manifest_quiz_eligible FROM documents"));
            assertEquals(1, integer(statement, "SELECT quiz_eligible FROM documents"));
            assertEquals("AUTO_CLASSIFIED", text(statement,
                    "SELECT manifest_review_status FROM passport_images"));
            assertEquals("VERIFIED", text(statement, "SELECT review_status FROM passport_images"));
            assertEquals(4, integer(statement, "SELECT COUNT(*) FROM review_decisions"));
            assertEquals(4, integer(statement,
                    "SELECT review_decision_count FROM catalogue_metadata"));
            PassportCatalogueValidator.validate(connection);
        }

        SQLitePassportRepository repository = new SQLitePassportRepository(provider);
        assertEquals(1, repository.findQuizEligibleCandidates().size());
        assertEquals(Set.of("continent:europe", "continent:asia"),
                repository.findQuizEligibleCandidates().getFirst().continentIds());

        var session = new QuizGenerationService(
                repository,
                Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC)
        ).generate(request());
        assertEquals(1, session.questions().size());
    }

    @Test
    void deactivationRemovesCandidateAndGeneratorStillFailsClosed() throws Exception {
        Fixture fixture = createFixture("disabled");
        ReviewDecisionSource active = ReviewDecisionSource.read(fixture.reviewSource());
        ReviewDecisionSource disabled = active.replacing(new ReviewDecisionSource.DecisionRecord(
                ReviewDecisionSource.RecordType.QUIZ_ACTIVATION,
                ReviewDecisionSource.TargetType.DOCUMENT,
                fixture.document().id(),
                ReviewTargetFingerprint.document(fixture.document()),
                ReviewDecisionSource.Decision.DISABLED,
                "reviewer.test",
                ReviewTestSupport.DECIDED_AT,
                "Explicit deactivation"
        ));
        Files.write(fixture.reviewSource(), disabled.canonicalBytes());

        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(
                temporaryDirectory.resolve("disabled.sqlite")
        );
        new PassportCatalogueImporter(provider).rebuild(fixture.manifestDirectory(), fixture.reviewSource());
        SQLitePassportRepository repository = new SQLitePassportRepository(provider);

        assertTrue(repository.findQuizEligibleCandidates().isEmpty());
        QuizGenerationException exception = assertThrows(
                QuizGenerationException.class,
                () -> new QuizGenerationService(repository, Clock.systemUTC()).generate(request())
        );
        assertEquals(QuizGenerationFailureReason.INSUFFICIENT_ELIGIBLE_DATA, exception.reason());
    }

    @Test
    void reviewDecisionsSurviveRebuildAndLogicalFingerprintIsDeterministic() throws Exception {
        Fixture fixture = createFixture("deterministic");
        SQLiteConnectionProvider first = new SQLiteConnectionProvider(
                temporaryDirectory.resolve("deterministic-one.sqlite")
        );
        PassportCatalogueImporter importer = new PassportCatalogueImporter(first);
        importer.rebuild(fixture.manifestDirectory(), fixture.reviewSource());
        String initial = PassportCatalogueFingerprint.sha256(first);

        importer.rebuild(fixture.manifestDirectory(), fixture.reviewSource());
        assertEquals(initial, PassportCatalogueFingerprint.sha256(first));

        SQLiteConnectionProvider second = new SQLiteConnectionProvider(
                temporaryDirectory.resolve("deterministic-two.sqlite")
        );
        new PassportCatalogueImporter(second).rebuild(fixture.manifestDirectory(), fixture.reviewSource());
        assertEquals(initial, PassportCatalogueFingerprint.sha256(second));
        try (Connection connection = second.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(4, integer(statement, "SELECT COUNT(*) FROM review_decisions"));
        }
    }

    @Test
    void staleReviewFailsBeforeExistingCatalogueMutation() throws Exception {
        Fixture fixture = createFixture("stale");
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(
                temporaryDirectory.resolve("stale.sqlite")
        );
        PassportCatalogueImporter importer = new PassportCatalogueImporter(provider);
        importer.rebuild(fixture.manifestDirectory(), fixture.reviewSource());
        String before = PassportCatalogueFingerprint.sha256(provider);

        ReviewDecisionSource valid = ReviewDecisionSource.read(fixture.reviewSource());
        ReviewDecisionSource stale = valid.replacing(new ReviewDecisionSource.DecisionRecord(
                ReviewDecisionSource.RecordType.REVIEW,
                ReviewDecisionSource.TargetType.IMAGE,
                fixture.image().id(),
                ReviewTestSupport.sha(99),
                ReviewDecisionSource.Decision.VERIFIED,
                "reviewer.test",
                ReviewTestSupport.DECIDED_AT,
                ""
        ));
        Files.write(fixture.reviewSource(), stale.canonicalBytes());

        ReviewValidationException exception = assertThrows(
                ReviewValidationException.class,
                () -> importer.rebuild(fixture.manifestDirectory(), fixture.reviewSource())
        );
        assertEquals(ReviewFailureReason.STALE_TARGET_REVISION, exception.reason());
        assertEquals(before, PassportCatalogueFingerprint.sha256(provider));
    }

    private Fixture createFixture(String name) throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve(name));
        Path manifestDirectory = Files.createDirectory(directory.resolve("manifest"));
        Path sourcePath = Path.of("Europe", "Test", "passport.png");
        String normalizedPath = SourceNormalizer.normalizedSourcePath(sourcePath.toString());
        String entityId = SourceNormalizer.stableId("entity:", "test-entity");
        String documentId = SourceNormalizer.stableId("document:", normalizedPath);
        String imageId = SourceNormalizer.stableId("image:", normalizedPath);
        ManifestCatalogue.EntityEntry entity = new ManifestCatalogue.EntityEntry(
                entityId,
                "continent:europe",
                List.of("continent:asia"),
                "test-entity",
                "Test Entity",
                ManifestCatalogue.EntityType.COUNTRY,
                List.of("test entity"),
                ManifestCatalogue.ReviewStatus.AUTO_CLASSIFIED,
                ""
        );
        ManifestCatalogue.DocumentEntry document = new ManifestCatalogue.DocumentEntry(
                documentId,
                entityId,
                DocumentType.ORDINARY_PASSPORT,
                "Test Passport",
                "",
                ManifestCatalogue.ReviewStatus.AUTO_CLASSIFIED,
                false,
                "",
                ""
        );
        ManifestCatalogue.ImageEntry image = new ManifestCatalogue.ImageEntry(
                imageId,
                documentId,
                normalizedPath,
                "passport.png",
                "image/png",
                "PNG",
                640,
                480,
                12_345,
                ReviewTestSupport.sha(7),
                ManifestCatalogue.ReviewStatus.AUTO_CLASSIFIED,
                "",
                ""
        );
        ManifestCatalogue catalogue = new ManifestCatalogue(
                new ManifestCatalogue.Summary(1, "source", 2, 1, 1, 1),
                List.of(
                        new ManifestCatalogue.ContinentEntry(
                                "continent:europe", "EUROPE", "Europe", "Europe", 1
                        ),
                        new ManifestCatalogue.ContinentEntry(
                                "continent:asia", "ASIA", "Asia", "Asia", 2
                        )
                ),
                List.of(entity),
                List.of(document),
                List.of(image),
                List.of()
        );
        catalogue.write(manifestDirectory);
        Path reviewSource = directory.resolve(ReviewDecisionSource.FILE_NAME);
        List<ReviewDecisionSource.DecisionRecord> records = new ArrayList<>(List.of(
                ReviewTestSupport.review(ReviewDecisionSource.TargetType.ENTITY, entity.id(),
                        ReviewTargetFingerprint.entity(entity), ReviewDecisionSource.Decision.VERIFIED),
                ReviewTestSupport.review(ReviewDecisionSource.TargetType.DOCUMENT, document.id(),
                        ReviewTargetFingerprint.document(document), ReviewDecisionSource.Decision.VERIFIED),
                ReviewTestSupport.review(ReviewDecisionSource.TargetType.IMAGE, image.id(),
                        ReviewTargetFingerprint.image(image), ReviewDecisionSource.Decision.VERIFIED),
                new ReviewDecisionSource.DecisionRecord(
                        ReviewDecisionSource.RecordType.QUIZ_ACTIVATION,
                        ReviewDecisionSource.TargetType.DOCUMENT,
                        document.id(),
                        ReviewTargetFingerprint.document(document),
                        ReviewDecisionSource.Decision.ENABLED,
                        "reviewer.test",
                        ReviewTestSupport.DECIDED_AT,
                        ""
                )
        ));
        Files.write(reviewSource, ReviewDecisionSource.of(records).canonicalBytes());
        return new Fixture(manifestDirectory, reviewSource, document, image);
    }

    private static QuizGenerationRequest request() {
        return new QuizGenerationRequest(
                1L,
                1L,
                QuizMode.TRAINING,
                1,
                2,
                Set.of(QuizQuestionType.DOCUMENT_TYPE),
                Set.of(),
                Set.of(),
                Set.of(),
                7L
        );
    }

    private static int integer(Statement statement, String sql) throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static String text(Statement statement, String sql) throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private record Fixture(
            Path manifestDirectory,
            Path reviewSource,
            ManifestCatalogue.DocumentEntry document,
            ManifestCatalogue.ImageEntry image
    ) {
    }
}
