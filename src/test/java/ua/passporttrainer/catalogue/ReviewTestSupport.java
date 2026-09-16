package ua.passporttrainer.catalogue;

import ua.passporttrainer.manifest.ManifestCatalogue;
import ua.passporttrainer.model.DocumentType;

import java.time.Instant;
import java.util.List;

final class ReviewTestSupport {

    static final String ENTITY_ID = id("entity:", 1);
    static final String DOCUMENT_ID = id("document:", 2);
    static final String IMAGE_ID = id("image:", 3);
    static final Instant DECIDED_AT = Instant.parse("2026-09-16T10:15:30Z");

    private ReviewTestSupport() {
    }

    static Fixture fixture() {
        return fixture(DocumentType.ORDINARY_PASSPORT, "", "", ManifestCatalogue.ReviewStatus.AUTO_CLASSIFIED);
    }

    static Fixture fixture(
            DocumentType documentType,
            String duplicateDocumentId,
            String duplicateImageId,
            ManifestCatalogue.ReviewStatus manifestStatus
    ) {
        ManifestCatalogue.EntityEntry entity = new ManifestCatalogue.EntityEntry(
                ENTITY_ID,
                "continent:europe",
                List.of("continent:asia"),
                "test-entity",
                "Test Entity",
                ManifestCatalogue.EntityType.COUNTRY,
                List.of("test source"),
                manifestStatus,
                "fixture entity note"
        );
        ManifestCatalogue.DocumentEntry document = new ManifestCatalogue.DocumentEntry(
                DOCUMENT_ID,
                ENTITY_ID,
                documentType,
                "Test Passport",
                "variant-a",
                manifestStatus,
                false,
                duplicateDocumentId,
                "fixture document note"
        );
        ManifestCatalogue.ImageEntry image = new ManifestCatalogue.ImageEntry(
                IMAGE_ID,
                DOCUMENT_ID,
                "Europe/Test/passport.png",
                "passport.png",
                "image/png",
                "PNG",
                640,
                480,
                12_345,
                sha(7),
                manifestStatus,
                duplicateImageId,
                "fixture image note"
        );
        ManifestCatalogue catalogue = new ManifestCatalogue(
                new ManifestCatalogue.Summary(1, "source", 1, 1, 1, 1),
                List.of(new ManifestCatalogue.ContinentEntry(
                        "continent:europe", "EUROPE", "Europe", "Europe", 1
                )),
                List.of(entity),
                List.of(document),
                List.of(image),
                List.of()
        );
        return new Fixture(catalogue, entity, document, image);
    }

    static ReviewDecisionSource.DecisionRecord review(
            ReviewDecisionSource.TargetType targetType,
            String targetId,
            String fingerprint,
            ReviewDecisionSource.Decision decision
    ) {
        String note = decision == ReviewDecisionSource.Decision.NEEDS_REVIEW
                || decision == ReviewDecisionSource.Decision.EXCLUDED
                ? "Explicit review reason"
                : "";
        return new ReviewDecisionSource.DecisionRecord(
                ReviewDecisionSource.RecordType.REVIEW,
                targetType,
                targetId,
                fingerprint,
                decision,
                "reviewer.test",
                DECIDED_AT,
                note
        );
    }

    static ReviewDecisionSource.DecisionRecord activation(
            Fixture fixture,
            ReviewDecisionSource.Decision decision
    ) {
        return new ReviewDecisionSource.DecisionRecord(
                ReviewDecisionSource.RecordType.QUIZ_ACTIVATION,
                ReviewDecisionSource.TargetType.DOCUMENT,
                fixture.document().id(),
                ReviewTargetFingerprint.document(fixture.document()),
                decision,
                "reviewer.test",
                DECIDED_AT,
                decision == ReviewDecisionSource.Decision.DISABLED ? "Explicitly disabled" : ""
        );
    }

    static ReviewDecisionSource verifiedSource(Fixture fixture, boolean activate) {
        List<ReviewDecisionSource.DecisionRecord> records = new java.util.ArrayList<>(List.of(
                review(ReviewDecisionSource.TargetType.ENTITY, fixture.entity().id(),
                        ReviewTargetFingerprint.entity(fixture.entity()), ReviewDecisionSource.Decision.VERIFIED),
                review(ReviewDecisionSource.TargetType.DOCUMENT, fixture.document().id(),
                        ReviewTargetFingerprint.document(fixture.document()), ReviewDecisionSource.Decision.VERIFIED),
                review(ReviewDecisionSource.TargetType.IMAGE, fixture.image().id(),
                        ReviewTargetFingerprint.image(fixture.image()), ReviewDecisionSource.Decision.VERIFIED)
        ));
        if (activate) {
            records.add(activation(fixture, ReviewDecisionSource.Decision.ENABLED));
        }
        return ReviewDecisionSource.of(records);
    }

    static String id(String prefix, int value) {
        return prefix + "%064x".formatted(value);
    }

    static String sha(int value) {
        return "%064x".formatted(value);
    }

    record Fixture(
            ManifestCatalogue catalogue,
            ManifestCatalogue.EntityEntry entity,
            ManifestCatalogue.DocumentEntry document,
            ManifestCatalogue.ImageEntry image
    ) {
    }
}
