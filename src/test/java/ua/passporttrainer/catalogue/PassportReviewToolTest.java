package ua.passporttrainer.catalogue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.manifest.ManifestCatalogue;
import ua.passporttrainer.manifest.SourceNormalizer;
import ua.passporttrainer.model.DocumentType;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassportReviewToolTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-16T10:15:30Z"), ZoneOffset.UTC
    );

    @TempDir
    Path temporaryDirectory;

    private Path manifestDirectory;
    private Path reviewSource;
    private ManifestCatalogue.EntityEntry entity;
    private ManifestCatalogue.DocumentEntry document;
    private ManifestCatalogue.ImageEntry image;

    @BeforeEach
    void createFixture() throws Exception {
        manifestDirectory = Files.createDirectory(temporaryDirectory.resolve("manifest"));
        reviewSource = temporaryDirectory.resolve(ReviewDecisionSource.FILE_NAME);
        String sourcePath = "Europe/Test/passport.png";
        String entityId = SourceNormalizer.stableId("entity:", "test-entity");
        String documentId = SourceNormalizer.stableId("document:", sourcePath);
        String imageId = SourceNormalizer.stableId("image:", sourcePath);
        entity = new ManifestCatalogue.EntityEntry(
                entityId, "continent:europe", List.of("continent:asia"), "test-entity", "Test Entity",
                ManifestCatalogue.EntityType.COUNTRY, List.of("test source"),
                ManifestCatalogue.ReviewStatus.AUTO_CLASSIFIED, ""
        );
        document = new ManifestCatalogue.DocumentEntry(
                documentId, entityId, DocumentType.ORDINARY_PASSPORT, "Test Passport", "",
                ManifestCatalogue.ReviewStatus.AUTO_CLASSIFIED, false, "", ""
        );
        image = new ManifestCatalogue.ImageEntry(
                imageId, documentId, sourcePath, "passport.png", "image/png", "PNG", 640, 480,
                12_345, ReviewTestSupport.sha(7), ManifestCatalogue.ReviewStatus.AUTO_CLASSIFIED, "", ""
        );
        new ManifestCatalogue(
                new ManifestCatalogue.Summary(1, "source", 2, 1, 1, 1),
                List.of(
                        new ManifestCatalogue.ContinentEntry(
                                "continent:europe", "EUROPE", "Europe", "Europe", 1
                        ),
                        new ManifestCatalogue.ContinentEntry(
                                "continent:asia", "ASIA", "Asia", "Asia", 2
                        )
                ),
                List.of(entity), List.of(document), List.of(image), List.of()
        ).write(manifestDirectory);
        Files.write(reviewSource, ReviewDecisionSource.empty().canonicalBytes());
    }

    @Test
    void inspectShowsStableIdentityAndCompleteReviewerContext() throws Exception {
        String output = run("inspect", manifestDirectory.toString(), reviewSource.toString(),
                "DOCUMENT", document.id());

        assertTrue(output.contains("targetId=" + document.id()));
        assertTrue(output.contains("entityName=Test Entity"));
        assertTrue(output.contains("entityContinents=continent:europe,continent:asia"));
        assertTrue(output.contains("documentTitle=Test Passport"));
        assertTrue(output.contains("documentType=ORDINARY_PASSPORT"));
        assertTrue(output.contains("imageSourcePath=Europe/Test/passport.png"));
        assertTrue(output.contains("imageId=" + image.id()));
        assertTrue(output.contains("imageDimensions=640x480"));
        assertTrue(output.contains("imageSha256=" + image.sha256()));
        assertTrue(output.contains("reviewDecision=PENDING"));
        assertTrue(output.contains("quizActivation=NOT_SET"));
    }

    @Test
    void explicitSingleTargetCommandsProduceCanonicalAuditableSource() throws Exception {
        run("decide", manifestDirectory.toString(), reviewSource.toString(),
                "ENTITY", entity.id(), "VERIFIED", "reviewer.test");
        run("decide", manifestDirectory.toString(), reviewSource.toString(),
                "DOCUMENT", document.id(), "VERIFIED", "reviewer.test");
        run("decide", manifestDirectory.toString(), reviewSource.toString(),
                "IMAGE", image.id(), "VERIFIED", "reviewer.test");
        run("activate", manifestDirectory.toString(), reviewSource.toString(),
                document.id(), "reviewer.test");

        ReviewDecisionSource source = ReviewDecisionSource.read(reviewSource);
        assertEquals(4, source.records().size());
        assertEquals(ReviewDecisionSource.Decision.ENABLED, source.find(
                ReviewDecisionSource.RecordType.QUIZ_ACTIVATION,
                ReviewDecisionSource.TargetType.DOCUMENT,
                document.id()
        ).decision());
        assertTrue(run("validate", manifestDirectory.toString(), reviewSource.toString())
                .contains("decisions=4 enabled=1"));
        assertTrue(run("report", manifestDirectory.toString(), reviewSource.toString())
                .contains("reviewed=3 pending=0"));
    }

    @Test
    void revocationRequiresExplicitDeactivationFirst() throws Exception {
        run("decide", manifestDirectory.toString(), reviewSource.toString(),
                "ENTITY", entity.id(), "VERIFIED", "reviewer.test");
        run("decide", manifestDirectory.toString(), reviewSource.toString(),
                "DOCUMENT", document.id(), "VERIFIED", "reviewer.test");
        run("decide", manifestDirectory.toString(), reviewSource.toString(),
                "IMAGE", image.id(), "VERIFIED", "reviewer.test");
        run("activate", manifestDirectory.toString(), reviewSource.toString(),
                document.id(), "reviewer.test");
        byte[] before = Files.readAllBytes(reviewSource);

        ReviewValidationException exception = assertThrows(
                ReviewValidationException.class,
                () -> run("decide", manifestDirectory.toString(), reviewSource.toString(),
                        "IMAGE", image.id(), "NEEDS_REVIEW", "reviewer.test", "Needs correction")
        );
        assertEquals(ReviewFailureReason.IMAGE_NOT_VERIFIED, exception.reason());
        org.junit.jupiter.api.Assertions.assertArrayEquals(before, Files.readAllBytes(reviewSource));

        run("deactivate", manifestDirectory.toString(), reviewSource.toString(),
                document.id(), "reviewer.test", "Explicit deactivation");
        run("decide", manifestDirectory.toString(), reviewSource.toString(),
                "IMAGE", image.id(), "NEEDS_REVIEW", "reviewer.test", "Needs correction");
        assertEquals(ReviewDecisionSource.Decision.NEEDS_REVIEW, ReviewDecisionSource.read(reviewSource).find(
                ReviewDecisionSource.RecordType.REVIEW,
                ReviewDecisionSource.TargetType.IMAGE,
                image.id()
        ).decision());
    }

    @Test
    void listingMayFilterButBulkMutationCommandsDoNotExist() throws Exception {
        String pending = run("list-pending", manifestDirectory.toString(), reviewSource.toString(), "ENTITY");
        assertTrue(pending.contains(entity.id()));
        org.junit.jupiter.api.Assertions.assertFalse(pending.contains(document.id()));

        assertThrows(IllegalArgumentException.class, () -> run(
                "verify-all", manifestDirectory.toString(), reviewSource.toString()
        ));
        assertThrows(IllegalArgumentException.class, () -> run(
                "activate-all", manifestDirectory.toString(), reviewSource.toString()
        ));
    }

    private static String run(String... arguments) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            PassportReviewTool.run(arguments, output, CLOCK);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
