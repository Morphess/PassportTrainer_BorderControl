package ua.passporttrainer.catalogue;

import ua.passporttrainer.manifest.ManifestCatalogue;
import ua.passporttrainer.manifest.PassportManifestValidator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Editorial use cases over a canonical manifest and one version-controlled review source. */
final class PassportReviewService {

    private final ManifestCatalogue catalogue;
    private final Path reviewSourcePath;
    private final Clock clock;
    private final Map<String, ManifestCatalogue.EntityEntry> entities;
    private final Map<String, ManifestCatalogue.DocumentEntry> documents;
    private final Map<String, ManifestCatalogue.ImageEntry> images;
    private final Map<String, ManifestCatalogue.ImageEntry> imagesByDocument;

    PassportReviewService(ManifestCatalogue catalogue, Path reviewSourcePath, Clock clock) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.reviewSourcePath = Objects.requireNonNull(reviewSourcePath, "reviewSourcePath");
        this.clock = Objects.requireNonNull(clock, "clock");
        PassportManifestValidator.validate(catalogue);
        this.entities = byEntityId(catalogue.entities());
        this.documents = byDocumentId(catalogue.documents());
        this.images = byImageId(catalogue.images());
        this.imagesByDocument = byDocument(catalogue.images());
    }

    static PassportReviewService open(Path manifestDirectory, Path reviewSourcePath, Clock clock) throws IOException {
        return new PassportReviewService(ManifestCatalogue.read(manifestDirectory), reviewSourcePath, clock);
    }

    ValidationSummary validate() throws IOException {
        ReviewDecisionSource source = readValidatedSource();
        return new ValidationSummary(
                source.records().size(),
                source.checksum(),
                source.records().stream()
                        .filter(record -> record.recordType() == ReviewDecisionSource.RecordType.QUIZ_ACTIVATION
                                && record.decision() == ReviewDecisionSource.Decision.ENABLED)
                        .count()
        );
    }

    List<TargetView> listPending(ReviewDecisionSource.TargetType filter) throws IOException {
        ReviewDecisionSource source = readValidatedSource();
        List<TargetView> result = new ArrayList<>();
        for (ReviewDecisionSource.TargetType targetType : ReviewDecisionSource.TargetType.values()) {
            if (filter != null && filter != targetType) {
                continue;
            }
            for (String targetId : targetIds(targetType)) {
                if (source.find(ReviewDecisionSource.RecordType.REVIEW, targetType, targetId) == null) {
                    result.add(view(source, targetType, targetId));
                }
            }
        }
        return List.copyOf(result);
    }

    List<TargetView> listReviewed(ReviewDecisionSource.TargetType filter) throws IOException {
        ReviewDecisionSource source = readValidatedSource();
        List<TargetView> result = new ArrayList<>();
        for (ReviewDecisionSource.DecisionRecord record : source.records()) {
            if (record.recordType() == ReviewDecisionSource.RecordType.REVIEW
                    && (filter == null || filter == record.targetType())) {
                result.add(view(source, record.targetType(), record.targetId()));
            }
        }
        return List.copyOf(result);
    }

    TargetView inspect(ReviewDecisionSource.TargetType targetType, String targetId) throws IOException {
        ReviewDecisionSource source = readValidatedSource();
        return view(source, targetType, targetId);
    }

    ValidationSummary decide(
            ReviewDecisionSource.TargetType targetType,
            String targetId,
            ReviewDecisionSource.Decision decision,
            String reviewer,
            String note
    ) throws IOException {
        if (decision != ReviewDecisionSource.Decision.VERIFIED
                && decision != ReviewDecisionSource.Decision.NEEDS_REVIEW
                && decision != ReviewDecisionSource.Decision.EXCLUDED) {
            throw new ReviewValidationException(
                    ReviewFailureReason.CONFLICTING_DECISION,
                    targetId,
                    "decide accepts only VERIFIED, NEEDS_REVIEW, or EXCLUDED"
            );
        }
        ReviewDecisionSource current = readValidatedSource();
        ReviewDecisionSource replacement = current.replacing(new ReviewDecisionSource.DecisionRecord(
                ReviewDecisionSource.RecordType.REVIEW,
                targetType,
                targetId,
                fingerprint(targetType, targetId),
                decision,
                reviewer,
                Instant.now(clock),
                note
        ));
        ReviewDecisionValidator.validate(catalogue, replacement);
        ReviewDecisionSource.writeAtomically(reviewSourcePath, current.checksum(), replacement);
        return summary(replacement);
    }

    ValidationSummary activate(String documentId, String reviewer, String note) throws IOException {
        return changeActivation(documentId, ReviewDecisionSource.Decision.ENABLED, reviewer, note);
    }

    ValidationSummary deactivate(String documentId, String reviewer, String note) throws IOException {
        return changeActivation(documentId, ReviewDecisionSource.Decision.DISABLED, reviewer, note);
    }

    Report report() throws IOException {
        ReviewDecisionSource source = readValidatedSource();
        Map<ReviewDecisionSource.Decision, Long> counts = new EnumMap<>(ReviewDecisionSource.Decision.class);
        for (ReviewDecisionSource.Decision decision : ReviewDecisionSource.Decision.values()) {
            counts.put(decision, 0L);
        }
        for (ReviewDecisionSource.DecisionRecord record : source.records()) {
            counts.compute(record.decision(), (ignored, count) -> count + 1);
        }
        long totalTargets = (long) entities.size() + documents.size() + images.size();
        long reviewedTargets = source.records().stream()
                .filter(record -> record.recordType() == ReviewDecisionSource.RecordType.REVIEW)
                .count();
        return new Report(
                totalTargets,
                reviewedTargets,
                totalTargets - reviewedTargets,
                Map.copyOf(counts),
                source.checksum()
        );
    }

    private ValidationSummary changeActivation(
            String documentId,
            ReviewDecisionSource.Decision decision,
            String reviewer,
            String note
    ) throws IOException {
        ManifestCatalogue.DocumentEntry document = documents.get(documentId);
        if (document == null) {
            throw new ReviewValidationException(
                    ReviewFailureReason.UNKNOWN_TARGET,
                    documentId,
                    "Unknown document target"
            );
        }
        ReviewDecisionSource current = readValidatedSource();
        ReviewDecisionSource replacement = current.replacing(new ReviewDecisionSource.DecisionRecord(
                ReviewDecisionSource.RecordType.QUIZ_ACTIVATION,
                ReviewDecisionSource.TargetType.DOCUMENT,
                documentId,
                ReviewTargetFingerprint.document(document),
                decision,
                reviewer,
                Instant.now(clock),
                note
        ));
        ReviewDecisionValidator.validate(catalogue, replacement);
        ReviewDecisionSource.writeAtomically(reviewSourcePath, current.checksum(), replacement);
        return summary(replacement);
    }

    private ReviewDecisionSource readValidatedSource() throws IOException {
        ReviewDecisionSource source = ReviewDecisionSource.read(reviewSourcePath);
        ReviewDecisionValidator.validate(catalogue, source);
        return source;
    }

    private TargetView view(
            ReviewDecisionSource source,
            ReviewDecisionSource.TargetType targetType,
            String targetId
    ) {
        ManifestCatalogue.EntityEntry entity;
        ManifestCatalogue.DocumentEntry document = null;
        ManifestCatalogue.ImageEntry image = null;
        switch (targetType) {
            case ENTITY -> {
                entity = entities.get(targetId);
                if (entity == null) {
                    throw unknown(targetId, "entity");
                }
            }
            case DOCUMENT -> {
                document = documents.get(targetId);
                if (document == null) {
                    throw unknown(targetId, "document");
                }
                entity = entities.get(document.entityId());
                image = imagesByDocument.get(document.id());
            }
            case IMAGE -> {
                image = images.get(targetId);
                if (image == null) {
                    throw unknown(targetId, "image");
                }
                document = documents.get(image.documentId());
                entity = entities.get(document.entityId());
            }
            default -> throw new IllegalStateException("Unsupported target type: " + targetType);
        }
        ReviewDecisionSource.DecisionRecord review = source.find(
                ReviewDecisionSource.RecordType.REVIEW, targetType, targetId
        );
        ReviewDecisionSource.DecisionRecord activation = document == null ? null : source.find(
                ReviewDecisionSource.RecordType.QUIZ_ACTIVATION,
                ReviewDecisionSource.TargetType.DOCUMENT,
                document.id()
        );
        return new TargetView(targetType, targetId, entity, document, image, review, activation);
    }

    private List<String> targetIds(ReviewDecisionSource.TargetType targetType) {
        return switch (targetType) {
            case ENTITY -> catalogue.entities().stream().map(ManifestCatalogue.EntityEntry::id).sorted().toList();
            case DOCUMENT -> catalogue.documents().stream().map(ManifestCatalogue.DocumentEntry::id).sorted().toList();
            case IMAGE -> catalogue.images().stream().map(ManifestCatalogue.ImageEntry::id).sorted().toList();
        };
    }

    private String fingerprint(ReviewDecisionSource.TargetType targetType, String targetId) {
        return switch (targetType) {
            case ENTITY -> {
                ManifestCatalogue.EntityEntry entity = entities.get(targetId);
                if (entity == null) {
                    throw unknown(targetId, "entity");
                }
                yield ReviewTargetFingerprint.entity(entity);
            }
            case DOCUMENT -> {
                ManifestCatalogue.DocumentEntry document = documents.get(targetId);
                if (document == null) {
                    throw unknown(targetId, "document");
                }
                yield ReviewTargetFingerprint.document(document);
            }
            case IMAGE -> {
                ManifestCatalogue.ImageEntry image = images.get(targetId);
                if (image == null) {
                    throw unknown(targetId, "image");
                }
                yield ReviewTargetFingerprint.image(image);
            }
        };
    }

    private static ValidationSummary summary(ReviewDecisionSource source) {
        return new ValidationSummary(
                source.records().size(),
                source.checksum(),
                source.records().stream()
                        .filter(record -> record.recordType() == ReviewDecisionSource.RecordType.QUIZ_ACTIVATION
                                && record.decision() == ReviewDecisionSource.Decision.ENABLED)
                        .count()
        );
    }

    private static ReviewValidationException unknown(String targetId, String type) {
        return new ReviewValidationException(
                ReviewFailureReason.UNKNOWN_TARGET,
                targetId,
                "Unknown " + type + " target"
        );
    }

    private static Map<String, ManifestCatalogue.EntityEntry> byEntityId(
            List<ManifestCatalogue.EntityEntry> values
    ) {
        Map<String, ManifestCatalogue.EntityEntry> result = new HashMap<>();
        values.forEach(value -> result.put(value.id(), value));
        return Map.copyOf(result);
    }

    private static Map<String, ManifestCatalogue.DocumentEntry> byDocumentId(
            List<ManifestCatalogue.DocumentEntry> values
    ) {
        Map<String, ManifestCatalogue.DocumentEntry> result = new HashMap<>();
        values.forEach(value -> result.put(value.id(), value));
        return Map.copyOf(result);
    }

    private static Map<String, ManifestCatalogue.ImageEntry> byImageId(
            List<ManifestCatalogue.ImageEntry> values
    ) {
        Map<String, ManifestCatalogue.ImageEntry> result = new HashMap<>();
        values.forEach(value -> result.put(value.id(), value));
        return Map.copyOf(result);
    }

    private static Map<String, ManifestCatalogue.ImageEntry> byDocument(
            List<ManifestCatalogue.ImageEntry> values
    ) {
        Map<String, ManifestCatalogue.ImageEntry> result = new HashMap<>();
        values.forEach(value -> result.put(value.documentId(), value));
        return Map.copyOf(result);
    }

    record ValidationSummary(int decisionCount, String sourceChecksum, long enabledCount) {
    }

    record Report(
            long totalTargets,
            long reviewedTargets,
            long pendingTargets,
            Map<ReviewDecisionSource.Decision, Long> decisionCounts,
            String sourceChecksum
    ) {
    }

    record TargetView(
            ReviewDecisionSource.TargetType targetType,
            String targetId,
            ManifestCatalogue.EntityEntry entity,
            ManifestCatalogue.DocumentEntry document,
            ManifestCatalogue.ImageEntry image,
            ReviewDecisionSource.DecisionRecord review,
            ReviewDecisionSource.DecisionRecord activation
    ) {
    }
}
