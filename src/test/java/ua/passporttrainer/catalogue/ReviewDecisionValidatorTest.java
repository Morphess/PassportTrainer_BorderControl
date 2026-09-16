package ua.passporttrainer.catalogue;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.manifest.ManifestCatalogue;
import ua.passporttrainer.model.DocumentType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewDecisionValidatorTest {

    @Test
    void decisionsAreIndependentAndActivationRequiresAllThreeVerifiedTargets() {
        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource empty = ReviewDecisionSource.empty();
        assertFalse(materialize(fixture, empty).quizEligibility().get(fixture.document().id()));

        ReviewDecisionSource entityOnly = ReviewDecisionSource.of(List.of(entityVerified(fixture)));
        assertFalse(materialize(fixture, entityOnly).quizEligibility().get(fixture.document().id()));

        ReviewDecisionSource entityAndDocument = ReviewDecisionSource.of(List.of(
                entityVerified(fixture), documentVerified(fixture)
        ));
        assertFalse(materialize(fixture, entityAndDocument).quizEligibility().get(fixture.document().id()));

        ReviewDecisionSource allVerified = ReviewTestSupport.verifiedSource(fixture, false);
        assertFalse(materialize(fixture, allVerified).quizEligibility().get(fixture.document().id()));

        ReviewDecisionValidator.ReviewMaterialization activated = materialize(
                fixture,
                ReviewTestSupport.verifiedSource(fixture, true)
        );
        assertTrue(activated.quizEligibility().get(fixture.document().id()));
        assertEquals(ManifestCatalogue.ReviewStatus.VERIFIED,
                activated.entityStatuses().get(fixture.entity().id()));
        assertEquals(ManifestCatalogue.ReviewStatus.VERIFIED,
                activated.documentStatuses().get(fixture.document().id()));
        assertEquals(ManifestCatalogue.ReviewStatus.VERIFIED,
                activated.imageStatuses().get(fixture.image().id()));
    }

    @Test
    void explicitDeactivationRemovesEligibility() {
        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource source = ReviewTestSupport.verifiedSource(fixture, true)
                .replacing(ReviewTestSupport.activation(fixture, ReviewDecisionSource.Decision.DISABLED));

        assertFalse(materialize(fixture, source).quizEligibility().get(fixture.document().id()));
    }

    @Test
    void enabledActivationBlocksSilentVerificationRevocation() {
        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource source = ReviewTestSupport.verifiedSource(fixture, true).replacing(
                ReviewTestSupport.review(
                        ReviewDecisionSource.TargetType.IMAGE,
                        fixture.image().id(),
                        ReviewTargetFingerprint.image(fixture.image()),
                        ReviewDecisionSource.Decision.NEEDS_REVIEW
                )
        );

        assertReason(ReviewFailureReason.IMAGE_NOT_VERIFIED, () -> materialize(fixture, source));
    }

    @Test
    void excludedUnknownAndNoncanonicalTargetsCannotActivate() {
        ReviewTestSupport.Fixture excluded = ReviewTestSupport.fixture(
                DocumentType.ORDINARY_PASSPORT, "", "", ManifestCatalogue.ReviewStatus.EXCLUDED
        );
        assertReason(ReviewFailureReason.SOURCE_EXCLUDED, () -> materialize(
                excluded, ReviewTestSupport.verifiedSource(excluded, false)
        ));

        ReviewTestSupport.Fixture unknown = ReviewTestSupport.fixture(
                DocumentType.UNKNOWN, "", "", ManifestCatalogue.ReviewStatus.NEEDS_REVIEW
        );
        assertReason(ReviewFailureReason.UNRESOLVED_DOCUMENT_TYPE, () -> materialize(
                unknown, ReviewTestSupport.verifiedSource(unknown, true)
        ));

        ReviewTestSupport.Fixture duplicateDocument = ReviewTestSupport.fixture(
                DocumentType.ORDINARY_PASSPORT,
                ReviewTestSupport.id("document:", 80),
                "",
                ManifestCatalogue.ReviewStatus.AUTO_CLASSIFIED
        );
        assertReason(ReviewFailureReason.NONCANONICAL_DOCUMENT, () -> materialize(
                duplicateDocument, ReviewTestSupport.verifiedSource(duplicateDocument, true)
        ));

        ReviewTestSupport.Fixture duplicateImage = ReviewTestSupport.fixture(
                DocumentType.ORDINARY_PASSPORT,
                "",
                ReviewTestSupport.id("image:", 81),
                ManifestCatalogue.ReviewStatus.AUTO_CLASSIFIED
        );
        assertReason(ReviewFailureReason.NONCANONICAL_IMAGE, () -> materialize(
                duplicateImage, ReviewTestSupport.verifiedSource(duplicateImage, true)
        ));
    }

    @Test
    void staleAndUnknownTargetsFailClosed() {
        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource stale = ReviewDecisionSource.of(List.of(
                ReviewTestSupport.review(
                        ReviewDecisionSource.TargetType.DOCUMENT,
                        fixture.document().id(),
                        ReviewTestSupport.sha(99),
                        ReviewDecisionSource.Decision.VERIFIED
                )
        ));
        assertReason(ReviewFailureReason.STALE_TARGET_REVISION, () -> materialize(fixture, stale));

        String missingId = ReviewTestSupport.id("entity:", 99);
        ReviewDecisionSource missing = ReviewDecisionSource.of(List.of(
                ReviewTestSupport.review(
                        ReviewDecisionSource.TargetType.ENTITY,
                        missingId,
                        ReviewTestSupport.sha(98),
                        ReviewDecisionSource.Decision.VERIFIED
                )
        ));
        assertReason(ReviewFailureReason.UNKNOWN_TARGET, () -> materialize(fixture, missing));
    }

    @Test
    void materialDocumentAndImageDriftProducesStaleTargetRevision() {
        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource decisions = ReviewTestSupport.verifiedSource(fixture, false);
        ManifestCatalogue.DocumentEntry document = fixture.document();
        ManifestCatalogue.ImageEntry image = fixture.image();

        List<ManifestCatalogue> drifted = List.of(
                with(fixture, new ManifestCatalogue.DocumentEntry(
                        document.id(), document.entityId(), DocumentType.NATIONAL_ID, document.titleUk(),
                        document.variant(), document.reviewStatus(), document.quizEligible(),
                        document.duplicateOfDocumentId(), document.notes()
                ), image),
                with(fixture, new ManifestCatalogue.DocumentEntry(
                        document.id(), ReviewTestSupport.id("entity:", 70), document.documentType(),
                        document.titleUk(), document.variant(), document.reviewStatus(), document.quizEligible(),
                        document.duplicateOfDocumentId(), document.notes()
                ), image),
                with(fixture, document, new ManifestCatalogue.ImageEntry(
                        image.id(), image.documentId(), "Other/path.png", "path.png", image.mimeType(),
                        image.format(), image.width(), image.height(), image.fileSize(), image.sha256(),
                        image.reviewStatus(), image.duplicateOfImageId(), image.notes()
                )),
                with(fixture, document, new ManifestCatalogue.ImageEntry(
                        image.id(), image.documentId(), image.sourcePath(), image.sourceFilename(), image.mimeType(),
                        image.format(), image.width(), image.height(), image.fileSize(), ReviewTestSupport.sha(71),
                        image.reviewStatus(), image.duplicateOfImageId(), image.notes()
                )),
                with(fixture, new ManifestCatalogue.DocumentEntry(
                        document.id(), document.entityId(), document.documentType(), document.titleUk(),
                        document.variant(), document.reviewStatus(), document.quizEligible(),
                        ReviewTestSupport.id("document:", 72), document.notes()
                ), new ManifestCatalogue.ImageEntry(
                        image.id(), image.documentId(), image.sourcePath(), image.sourceFilename(), image.mimeType(),
                        image.format(), image.width(), image.height(), image.fileSize(), image.sha256(),
                        image.reviewStatus(), ReviewTestSupport.id("image:", 73), image.notes()
                ))
        );

        for (ManifestCatalogue catalogue : drifted) {
            assertReason(
                    ReviewFailureReason.STALE_TARGET_REVISION,
                    () -> ReviewDecisionValidator.validate(catalogue, decisions)
            );
        }
    }

    @Test
    void activationReportsEachMissingVerifiedDependency() {
        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource entityMissing = ReviewDecisionSource.of(List.of(
                documentVerified(fixture), imageVerified(fixture),
                ReviewTestSupport.activation(fixture, ReviewDecisionSource.Decision.ENABLED)
        ));
        assertReason(ReviewFailureReason.ENTITY_NOT_VERIFIED, () -> materialize(fixture, entityMissing));

        ReviewDecisionSource documentMissing = ReviewDecisionSource.of(List.of(
                entityVerified(fixture), imageVerified(fixture),
                ReviewTestSupport.activation(fixture, ReviewDecisionSource.Decision.ENABLED)
        ));
        assertReason(ReviewFailureReason.DOCUMENT_NOT_VERIFIED, () -> materialize(fixture, documentMissing));

        ReviewDecisionSource imageMissing = ReviewDecisionSource.of(List.of(
                entityVerified(fixture), documentVerified(fixture),
                ReviewTestSupport.activation(fixture, ReviewDecisionSource.Decision.ENABLED)
        ));
        assertReason(ReviewFailureReason.IMAGE_NOT_VERIFIED, () -> materialize(fixture, imageMissing));
    }

    private static ReviewDecisionSource.DecisionRecord entityVerified(ReviewTestSupport.Fixture fixture) {
        return ReviewTestSupport.review(ReviewDecisionSource.TargetType.ENTITY, fixture.entity().id(),
                ReviewTargetFingerprint.entity(fixture.entity()), ReviewDecisionSource.Decision.VERIFIED);
    }

    private static ReviewDecisionSource.DecisionRecord documentVerified(ReviewTestSupport.Fixture fixture) {
        return ReviewTestSupport.review(ReviewDecisionSource.TargetType.DOCUMENT, fixture.document().id(),
                ReviewTargetFingerprint.document(fixture.document()), ReviewDecisionSource.Decision.VERIFIED);
    }

    private static ReviewDecisionSource.DecisionRecord imageVerified(ReviewTestSupport.Fixture fixture) {
        return ReviewTestSupport.review(ReviewDecisionSource.TargetType.IMAGE, fixture.image().id(),
                ReviewTargetFingerprint.image(fixture.image()), ReviewDecisionSource.Decision.VERIFIED);
    }

    private static ReviewDecisionValidator.ReviewMaterialization materialize(
            ReviewTestSupport.Fixture fixture,
            ReviewDecisionSource source
    ) {
        return ReviewDecisionValidator.validate(fixture.catalogue(), source);
    }

    private static ManifestCatalogue with(
            ReviewTestSupport.Fixture fixture,
            ManifestCatalogue.DocumentEntry document,
            ManifestCatalogue.ImageEntry image
    ) {
        ManifestCatalogue original = fixture.catalogue();
        return new ManifestCatalogue(
                original.summary(),
                original.continents(),
                original.entities(),
                List.of(document),
                List.of(image),
                original.aliases()
        );
    }

    private static void assertReason(ReviewFailureReason reason, Runnable operation) {
        ReviewValidationException exception = assertThrows(ReviewValidationException.class, operation::run);
        assertEquals(reason, exception.reason());
    }
}
