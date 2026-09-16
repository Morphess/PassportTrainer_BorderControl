package ua.passporttrainer.catalogue;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.manifest.ManifestCatalogue;
import ua.passporttrainer.model.DocumentType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ReviewTargetFingerprintTest {

    @Test
    void schemaVersionIsExplicit() {
        assertEquals(1, ReviewTargetFingerprint.SCHEMA_VERSION);
    }

    @Test
    void entityFingerprintCoversMaterialIdentityAndProvenanceButNotNotesOrSetOrdering() {
        ManifestCatalogue.EntityEntry base = ReviewTestSupport.fixture().entity();
        String fingerprint = ReviewTargetFingerprint.entity(base);

        for (ManifestCatalogue.EntityEntry changed : List.of(
                entity(base, ReviewTestSupport.id("entity:", 40), base.canonicalKey(), base.entityType(),
                        base.displayNameUk(), base.primaryContinentId(), base.alternateContinentIds(),
                        base.sourceNames(), base.reviewStatus(), base.notes()),
                entity(base, base.id(), "other-key", base.entityType(), base.displayNameUk(),
                        base.primaryContinentId(), base.alternateContinentIds(), base.sourceNames(),
                        base.reviewStatus(), base.notes()),
                entity(base, base.id(), base.canonicalKey(), ManifestCatalogue.EntityType.TERRITORY,
                        base.displayNameUk(), base.primaryContinentId(), base.alternateContinentIds(),
                        base.sourceNames(), base.reviewStatus(), base.notes()),
                entity(base, base.id(), base.canonicalKey(), base.entityType(), "Other Entity",
                        base.primaryContinentId(), base.alternateContinentIds(), base.sourceNames(),
                        base.reviewStatus(), base.notes()),
                entity(base, base.id(), base.canonicalKey(), base.entityType(), base.displayNameUk(),
                        "continent:africa", base.alternateContinentIds(), base.sourceNames(),
                        base.reviewStatus(), base.notes()),
                entity(base, base.id(), base.canonicalKey(), base.entityType(), base.displayNameUk(),
                        base.primaryContinentId(), List.of("continent:oceania"), base.sourceNames(),
                        base.reviewStatus(), base.notes()),
                entity(base, base.id(), base.canonicalKey(), base.entityType(), base.displayNameUk(),
                        base.primaryContinentId(), base.alternateContinentIds(), List.of("other source"),
                        base.reviewStatus(), base.notes()),
                entity(base, base.id(), base.canonicalKey(), base.entityType(), base.displayNameUk(),
                        base.primaryContinentId(), base.alternateContinentIds(), base.sourceNames(),
                        ManifestCatalogue.ReviewStatus.NEEDS_REVIEW, base.notes())
        )) {
            assertNotEquals(fingerprint, ReviewTargetFingerprint.entity(changed));
        }

        ManifestCatalogue.EntityEntry cosmeticNote = entity(base, base.id(), base.canonicalKey(), base.entityType(),
                base.displayNameUk(), base.primaryContinentId(), base.alternateContinentIds(), base.sourceNames(),
                base.reviewStatus(), "changed presentation note");
        assertEquals(fingerprint, ReviewTargetFingerprint.entity(cosmeticNote));

        ManifestCatalogue.EntityEntry reordered = entity(base, base.id(), base.canonicalKey(), base.entityType(),
                base.displayNameUk(), base.primaryContinentId(), List.of("continent:asia", "continent:africa"),
                List.of("z source", "a source"), base.reviewStatus(), base.notes());
        ManifestCatalogue.EntityEntry reorderedAgain = entity(base, base.id(), base.canonicalKey(), base.entityType(),
                base.displayNameUk(), base.primaryContinentId(), List.of("continent:africa", "continent:asia"),
                List.of("a source", "z source"), base.reviewStatus(), base.notes());
        assertEquals(ReviewTargetFingerprint.entity(reordered), ReviewTargetFingerprint.entity(reorderedAgain));
    }

    @Test
    void documentFingerprintCoversEveryApprovedMaterialFieldButNotNotes() {
        ManifestCatalogue.DocumentEntry base = ReviewTestSupport.fixture().document();
        String fingerprint = ReviewTargetFingerprint.document(base);

        for (ManifestCatalogue.DocumentEntry changed : List.of(
                document(base, ReviewTestSupport.id("document:", 41), base.entityId(), base.documentType(),
                        base.titleUk(), base.variant(), base.reviewStatus(), base.quizEligible(),
                        base.duplicateOfDocumentId(), base.notes()),
                document(base, base.id(), ReviewTestSupport.id("entity:", 42), base.documentType(),
                        base.titleUk(), base.variant(), base.reviewStatus(), base.quizEligible(),
                        base.duplicateOfDocumentId(), base.notes()),
                document(base, base.id(), base.entityId(), DocumentType.NATIONAL_ID,
                        base.titleUk(), base.variant(), base.reviewStatus(), base.quizEligible(),
                        base.duplicateOfDocumentId(), base.notes()),
                document(base, base.id(), base.entityId(), base.documentType(), "Other title", base.variant(),
                        base.reviewStatus(), base.quizEligible(), base.duplicateOfDocumentId(), base.notes()),
                document(base, base.id(), base.entityId(), base.documentType(), base.titleUk(), "other-variant",
                        base.reviewStatus(), base.quizEligible(), base.duplicateOfDocumentId(), base.notes()),
                document(base, base.id(), base.entityId(), base.documentType(), base.titleUk(), base.variant(),
                        ManifestCatalogue.ReviewStatus.NEEDS_REVIEW, base.quizEligible(),
                        base.duplicateOfDocumentId(), base.notes()),
                document(base, base.id(), base.entityId(), base.documentType(), base.titleUk(), base.variant(),
                        base.reviewStatus(), true, base.duplicateOfDocumentId(), base.notes()),
                document(base, base.id(), base.entityId(), base.documentType(), base.titleUk(), base.variant(),
                        base.reviewStatus(), base.quizEligible(), ReviewTestSupport.id("document:", 43), base.notes())
        )) {
            assertNotEquals(fingerprint, ReviewTargetFingerprint.document(changed));
        }

        assertEquals(fingerprint, ReviewTargetFingerprint.document(document(
                base, base.id(), base.entityId(), base.documentType(), base.titleUk(), base.variant(),
                base.reviewStatus(), base.quizEligible(), base.duplicateOfDocumentId(), "changed note"
        )));
    }

    @Test
    void imageFingerprintCoversTechnicalIdentityAndContentButNotNotes() {
        ManifestCatalogue.ImageEntry base = ReviewTestSupport.fixture().image();
        String fingerprint = ReviewTargetFingerprint.image(base);

        for (ManifestCatalogue.ImageEntry changed : List.of(
                image(base, ReviewTestSupport.id("image:", 50), base.documentId(), base.sourcePath(),
                        base.sourceFilename(), base.mimeType(), base.format(), base.width(), base.height(),
                        base.fileSize(), base.sha256(), base.reviewStatus(), base.duplicateOfImageId(), base.notes()),
                image(base, base.id(), ReviewTestSupport.id("document:", 51), base.sourcePath(),
                        base.sourceFilename(), base.mimeType(), base.format(), base.width(), base.height(),
                        base.fileSize(), base.sha256(), base.reviewStatus(), base.duplicateOfImageId(), base.notes()),
                image(base, base.id(), base.documentId(), "Other/path.png", "path.png", base.mimeType(),
                        base.format(), base.width(), base.height(), base.fileSize(), base.sha256(),
                        base.reviewStatus(), base.duplicateOfImageId(), base.notes()),
                image(base, base.id(), base.documentId(), base.sourcePath(), base.sourceFilename(), "image/jpeg",
                        "JPEG", base.width(), base.height(), base.fileSize(), base.sha256(),
                        base.reviewStatus(), base.duplicateOfImageId(), base.notes()),
                image(base, base.id(), base.documentId(), base.sourcePath(), base.sourceFilename(), base.mimeType(),
                        base.format(), 641, base.height(), base.fileSize(), base.sha256(),
                        base.reviewStatus(), base.duplicateOfImageId(), base.notes()),
                image(base, base.id(), base.documentId(), base.sourcePath(), base.sourceFilename(), base.mimeType(),
                        base.format(), base.width(), 481, base.fileSize(), base.sha256(),
                        base.reviewStatus(), base.duplicateOfImageId(), base.notes()),
                image(base, base.id(), base.documentId(), base.sourcePath(), base.sourceFilename(), base.mimeType(),
                        base.format(), base.width(), base.height(), 12_346, base.sha256(),
                        base.reviewStatus(), base.duplicateOfImageId(), base.notes()),
                image(base, base.id(), base.documentId(), base.sourcePath(), base.sourceFilename(), base.mimeType(),
                        base.format(), base.width(), base.height(), base.fileSize(), ReviewTestSupport.sha(8),
                        base.reviewStatus(), base.duplicateOfImageId(), base.notes()),
                image(base, base.id(), base.documentId(), base.sourcePath(), base.sourceFilename(), base.mimeType(),
                        base.format(), base.width(), base.height(), base.fileSize(), base.sha256(),
                        ManifestCatalogue.ReviewStatus.NEEDS_REVIEW, base.duplicateOfImageId(), base.notes()),
                image(base, base.id(), base.documentId(), base.sourcePath(), base.sourceFilename(), base.mimeType(),
                        base.format(), base.width(), base.height(), base.fileSize(), base.sha256(), base.reviewStatus(),
                        ReviewTestSupport.id("image:", 52), base.notes())
        )) {
            assertNotEquals(fingerprint, ReviewTargetFingerprint.image(changed));
        }

        assertEquals(fingerprint, ReviewTargetFingerprint.image(image(
                base, base.id(), base.documentId(), base.sourcePath(), base.sourceFilename(), base.mimeType(),
                base.format(), base.width(), base.height(), base.fileSize(), base.sha256(), base.reviewStatus(),
                base.duplicateOfImageId(), "changed note"
        )));
    }

    private static ManifestCatalogue.EntityEntry entity(
            ManifestCatalogue.EntityEntry ignored,
            String id,
            String canonicalKey,
            ManifestCatalogue.EntityType entityType,
            String displayName,
            String primaryContinent,
            List<String> alternateContinents,
            List<String> sourceNames,
            ManifestCatalogue.ReviewStatus status,
            String notes
    ) {
        return new ManifestCatalogue.EntityEntry(id, primaryContinent, alternateContinents, canonicalKey,
                displayName, entityType, sourceNames, status, notes);
    }

    private static ManifestCatalogue.DocumentEntry document(
            ManifestCatalogue.DocumentEntry ignored,
            String id,
            String entityId,
            DocumentType type,
            String title,
            String variant,
            ManifestCatalogue.ReviewStatus status,
            boolean eligible,
            String duplicate,
            String notes
    ) {
        return new ManifestCatalogue.DocumentEntry(
                id, entityId, type, title, variant, status, eligible, duplicate, notes
        );
    }

    private static ManifestCatalogue.ImageEntry image(
            ManifestCatalogue.ImageEntry ignored,
            String id,
            String documentId,
            String path,
            String filename,
            String mimeType,
            String format,
            int width,
            int height,
            long size,
            String sha,
            ManifestCatalogue.ReviewStatus status,
            String duplicate,
            String notes
    ) {
        return new ManifestCatalogue.ImageEntry(
                id, documentId, path, filename, mimeType, format, width, height, size, sha, status, duplicate, notes
        );
    }
}
