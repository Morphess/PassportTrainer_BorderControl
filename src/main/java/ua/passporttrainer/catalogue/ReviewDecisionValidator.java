package ua.passporttrainer.catalogue;

import ua.passporttrainer.manifest.ManifestCatalogue;
import ua.passporttrainer.model.DocumentType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ua.passporttrainer.catalogue.ReviewDecisionSource.Decision;
import static ua.passporttrainer.catalogue.ReviewDecisionSource.RecordType;
import static ua.passporttrainer.catalogue.ReviewDecisionSource.TargetType;

/** Validates review decisions against one exact Stage 5 manifest and computes effective runtime state. */
final class ReviewDecisionValidator {

    private ReviewDecisionValidator() {
    }

    static ReviewMaterialization validate(
            ManifestCatalogue catalogue,
            ReviewDecisionSource source
    ) {
        Map<String, ManifestCatalogue.EntityEntry> entities = byEntityId(catalogue.entities());
        Map<String, ManifestCatalogue.DocumentEntry> documents = byDocumentId(catalogue.documents());
        Map<String, ManifestCatalogue.ImageEntry> images = byImageId(catalogue.images());
        Map<String, ManifestCatalogue.ImageEntry> imagesByDocument = imagesByDocument(catalogue.images());

        Map<String, ManifestCatalogue.ReviewStatus> entityStatuses = baseEntityStatuses(catalogue.entities());
        Map<String, ManifestCatalogue.ReviewStatus> documentStatuses = baseDocumentStatuses(catalogue.documents());
        Map<String, ManifestCatalogue.ReviewStatus> imageStatuses = baseImageStatuses(catalogue.images());

        for (ReviewDecisionSource.DecisionRecord record : source.records()) {
            requireCurrentFingerprint(record, entities, documents, images);
            if (record.recordType() != RecordType.REVIEW) {
                continue;
            }
            ManifestCatalogue.ReviewStatus effectiveStatus = reviewStatus(record.decision());
            switch (record.targetType()) {
                case ENTITY -> {
                    ManifestCatalogue.EntityEntry entity = entities.get(record.targetId());
                    rejectExcludedApproval(record, entity.reviewStatus());
                    entityStatuses.put(entity.id(), effectiveStatus);
                }
                case DOCUMENT -> {
                    ManifestCatalogue.DocumentEntry document = documents.get(record.targetId());
                    rejectExcludedApproval(record, document.reviewStatus());
                    if (record.decision() == Decision.VERIFIED
                            && document.documentType() == DocumentType.UNKNOWN) {
                        throw failure(ReviewFailureReason.UNRESOLVED_DOCUMENT_TYPE, record,
                                "UNKNOWN document type cannot be verified");
                    }
                    documentStatuses.put(document.id(), effectiveStatus);
                }
                case IMAGE -> {
                    ManifestCatalogue.ImageEntry image = images.get(record.targetId());
                    rejectExcludedApproval(record, image.reviewStatus());
                    imageStatuses.put(image.id(), effectiveStatus);
                }
            }
        }

        Map<String, Boolean> quizEligibility = new LinkedHashMap<>();
        for (ManifestCatalogue.DocumentEntry document : catalogue.documents()) {
            quizEligibility.put(document.id(), false);
        }
        for (ReviewDecisionSource.DecisionRecord record : source.records()) {
            if (record.recordType() != RecordType.QUIZ_ACTIVATION) {
                continue;
            }
            ManifestCatalogue.DocumentEntry document = documents.get(record.targetId());
            if (record.decision() == Decision.DISABLED) {
                quizEligibility.put(document.id(), false);
                continue;
            }
            validateActivation(
                    record,
                    document,
                    entities.get(document.entityId()),
                    imagesByDocument.get(document.id()),
                    entityStatuses,
                    documentStatuses,
                    imageStatuses
            );
            quizEligibility.put(document.id(), true);
        }

        return new ReviewMaterialization(
                Map.copyOf(entityStatuses),
                Map.copyOf(documentStatuses),
                Map.copyOf(imageStatuses),
                Map.copyOf(quizEligibility),
                source
        );
    }

    private static void validateActivation(
            ReviewDecisionSource.DecisionRecord record,
            ManifestCatalogue.DocumentEntry document,
            ManifestCatalogue.EntityEntry entity,
            ManifestCatalogue.ImageEntry image,
            Map<String, ManifestCatalogue.ReviewStatus> entityStatuses,
            Map<String, ManifestCatalogue.ReviewStatus> documentStatuses,
            Map<String, ManifestCatalogue.ReviewStatus> imageStatuses
    ) {
        if (document.documentType() == DocumentType.UNKNOWN) {
            throw failure(ReviewFailureReason.UNRESOLVED_DOCUMENT_TYPE, record,
                    "UNKNOWN document type cannot be activated");
        }
        if (!document.duplicateOfDocumentId().isEmpty()) {
            throw failure(ReviewFailureReason.NONCANONICAL_DOCUMENT, record,
                    "Duplicate document cannot be activated");
        }
        if (image == null || !image.duplicateOfImageId().isEmpty()) {
            throw failure(ReviewFailureReason.NONCANONICAL_IMAGE, record,
                    "Document does not have a canonical image");
        }
        if (entityStatuses.get(entity.id()) != ManifestCatalogue.ReviewStatus.VERIFIED) {
            throw failure(ReviewFailureReason.ENTITY_NOT_VERIFIED, record,
                    "Entity must be VERIFIED; explicitly disable activation before revoking it");
        }
        if (documentStatuses.get(document.id()) != ManifestCatalogue.ReviewStatus.VERIFIED) {
            throw failure(ReviewFailureReason.DOCUMENT_NOT_VERIFIED, record,
                    "Document must be VERIFIED; explicitly disable activation before revoking it");
        }
        if (imageStatuses.get(image.id()) != ManifestCatalogue.ReviewStatus.VERIFIED) {
            throw failure(ReviewFailureReason.IMAGE_NOT_VERIFIED, record,
                    "Image must be VERIFIED; explicitly disable activation before revoking it");
        }
    }

    private static void requireCurrentFingerprint(
            ReviewDecisionSource.DecisionRecord record,
            Map<String, ManifestCatalogue.EntityEntry> entities,
            Map<String, ManifestCatalogue.DocumentEntry> documents,
            Map<String, ManifestCatalogue.ImageEntry> images
    ) {
        String expected = switch (record.targetType()) {
            case ENTITY -> {
                ManifestCatalogue.EntityEntry entity = entities.get(record.targetId());
                if (entity == null) {
                    throw failure(ReviewFailureReason.UNKNOWN_TARGET, record, "Unknown entity target");
                }
                yield ReviewTargetFingerprint.entity(entity);
            }
            case DOCUMENT -> {
                ManifestCatalogue.DocumentEntry document = documents.get(record.targetId());
                if (document == null) {
                    throw failure(ReviewFailureReason.UNKNOWN_TARGET, record, "Unknown document target");
                }
                yield ReviewTargetFingerprint.document(document);
            }
            case IMAGE -> {
                ManifestCatalogue.ImageEntry image = images.get(record.targetId());
                if (image == null) {
                    throw failure(ReviewFailureReason.UNKNOWN_TARGET, record, "Unknown image target");
                }
                yield ReviewTargetFingerprint.image(image);
            }
        };
        if (!expected.equals(record.targetFingerprintSha256())) {
            throw failure(ReviewFailureReason.STALE_TARGET_REVISION, record,
                    "Review target changed; expected fingerprint " + expected
                            + " but decision contains " + record.targetFingerprintSha256());
        }
    }

    private static void rejectExcludedApproval(
            ReviewDecisionSource.DecisionRecord record,
            ManifestCatalogue.ReviewStatus manifestStatus
    ) {
        if (manifestStatus == ManifestCatalogue.ReviewStatus.EXCLUDED
                && record.decision() == Decision.VERIFIED) {
            throw failure(ReviewFailureReason.SOURCE_EXCLUDED, record,
                    "Stage 5 EXCLUDED target cannot be approved");
        }
    }

    private static ManifestCatalogue.ReviewStatus reviewStatus(Decision decision) {
        return switch (decision) {
            case VERIFIED -> ManifestCatalogue.ReviewStatus.VERIFIED;
            case NEEDS_REVIEW -> ManifestCatalogue.ReviewStatus.NEEDS_REVIEW;
            case EXCLUDED -> ManifestCatalogue.ReviewStatus.EXCLUDED;
            case ENABLED, DISABLED -> throw new IllegalArgumentException("Not a review status: " + decision);
        };
    }

    private static ManifestCatalogue.ReviewStatus pendingStatus(ManifestCatalogue.ReviewStatus status) {
        return status == ManifestCatalogue.ReviewStatus.VERIFIED
                ? ManifestCatalogue.ReviewStatus.NEEDS_REVIEW
                : status;
    }

    private static Map<String, ManifestCatalogue.EntityEntry> byEntityId(
            List<ManifestCatalogue.EntityEntry> entries
    ) {
        Map<String, ManifestCatalogue.EntityEntry> result = new HashMap<>();
        for (ManifestCatalogue.EntityEntry entry : entries) {
            result.put(entry.id(), entry);
        }
        return Map.copyOf(result);
    }

    private static Map<String, ManifestCatalogue.DocumentEntry> byDocumentId(
            List<ManifestCatalogue.DocumentEntry> entries
    ) {
        Map<String, ManifestCatalogue.DocumentEntry> result = new HashMap<>();
        for (ManifestCatalogue.DocumentEntry entry : entries) {
            result.put(entry.id(), entry);
        }
        return Map.copyOf(result);
    }

    private static Map<String, ManifestCatalogue.ImageEntry> byImageId(
            List<ManifestCatalogue.ImageEntry> entries
    ) {
        Map<String, ManifestCatalogue.ImageEntry> result = new HashMap<>();
        for (ManifestCatalogue.ImageEntry entry : entries) {
            result.put(entry.id(), entry);
        }
        return Map.copyOf(result);
    }

    private static Map<String, ManifestCatalogue.ImageEntry> imagesByDocument(
            List<ManifestCatalogue.ImageEntry> entries
    ) {
        Map<String, ManifestCatalogue.ImageEntry> result = new HashMap<>();
        for (ManifestCatalogue.ImageEntry entry : entries) {
            result.put(entry.documentId(), entry);
        }
        return Map.copyOf(result);
    }

    private static Map<String, ManifestCatalogue.ReviewStatus> baseEntityStatuses(
            List<ManifestCatalogue.EntityEntry> entries
    ) {
        Map<String, ManifestCatalogue.ReviewStatus> result = new LinkedHashMap<>();
        for (ManifestCatalogue.EntityEntry entry : entries) {
            result.put(entry.id(), pendingStatus(entry.reviewStatus()));
        }
        return result;
    }

    private static Map<String, ManifestCatalogue.ReviewStatus> baseDocumentStatuses(
            List<ManifestCatalogue.DocumentEntry> entries
    ) {
        Map<String, ManifestCatalogue.ReviewStatus> result = new LinkedHashMap<>();
        for (ManifestCatalogue.DocumentEntry entry : entries) {
            result.put(entry.id(), pendingStatus(entry.reviewStatus()));
        }
        return result;
    }

    private static Map<String, ManifestCatalogue.ReviewStatus> baseImageStatuses(
            List<ManifestCatalogue.ImageEntry> entries
    ) {
        Map<String, ManifestCatalogue.ReviewStatus> result = new LinkedHashMap<>();
        for (ManifestCatalogue.ImageEntry entry : entries) {
            result.put(entry.id(), pendingStatus(entry.reviewStatus()));
        }
        return result;
    }

    private static ReviewValidationException failure(
            ReviewFailureReason reason,
            ReviewDecisionSource.DecisionRecord record,
            String message
    ) {
        return new ReviewValidationException(reason, record.targetId(), message);
    }

    record ReviewMaterialization(
            Map<String, ManifestCatalogue.ReviewStatus> entityStatuses,
            Map<String, ManifestCatalogue.ReviewStatus> documentStatuses,
            Map<String, ManifestCatalogue.ReviewStatus> imageStatuses,
            Map<String, Boolean> quizEligibility,
            ReviewDecisionSource source
    ) {
    }
}
