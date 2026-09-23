package ua.passporttrainer.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.QuizCandidate;
import ua.passporttrainer.presentation.LearningDocumentPresentation;
import ua.passporttrainer.presentation.LearningEntityPresentation;
import ua.passporttrainer.repository.PassportRepository;

/** Provides deterministic passive Learning material from the authoritative eligible-candidate query. */
public final class LearningService {

    private static final Comparator<QuizCandidate> CANDIDATE_ORDER = Comparator
            .comparing(QuizCandidate::entityDisplayName)
            .thenComparing(QuizCandidate::entityId)
            .thenComparingInt(candidate -> candidate.documentType().ordinal())
            .thenComparing(QuizCandidate::documentTitle)
            .thenComparing(QuizCandidate::documentId)
            .thenComparing(QuizCandidate::imageId);

    private final PassportRepository passportRepository;

    public LearningService(PassportRepository passportRepository) {
        this.passportRepository = Objects.requireNonNull(passportRepository, "passportRepository");
    }

    public List<LearningEntityPresentation> findEligibleEntities() {
        Map<String, EntityAccumulator> entities = new LinkedHashMap<>();
        for (QuizCandidate candidate : eligibleDocuments()) {
            entities.computeIfAbsent(
                    candidate.entityId(),
                    ignored -> new EntityAccumulator(candidate.entityId(), candidate.entityDisplayName())
            ).documentCount++;
        }

        List<LearningEntityPresentation> result = new ArrayList<>(entities.size());
        for (EntityAccumulator entity : entities.values()) {
            result.add(new LearningEntityPresentation(
                    entity.entityId,
                    entity.displayName,
                    entity.documentCount
            ));
        }
        return List.copyOf(result);
    }

    public List<LearningDocumentPresentation> findEligibleDocuments(String entityId) {
        Objects.requireNonNull(entityId, "entityId");
        if (entityId.isBlank()) {
            throw new IllegalArgumentException("entityId must not be blank");
        }

        List<LearningDocumentPresentation> result = new ArrayList<>();
        for (QuizCandidate candidate : eligibleDocuments()) {
            if (!candidate.entityId().equals(entityId)) {
                continue;
            }
            String variant = passportRepository.findById(candidate.documentId())
                    .map(PassportDocument::variant)
                    .orElse("");
            result.add(new LearningDocumentPresentation(
                    candidate.entityId(),
                    candidate.entityDisplayName(),
                    candidate.documentId(),
                    candidate.imageId(),
                    candidate.imageRelativePath(),
                    "documentType." + candidate.documentType().name(),
                    candidate.documentTitle(),
                    variant
            ));
        }
        return List.copyOf(result);
    }

    private List<QuizCandidate> eligibleDocuments() {
        List<QuizCandidate> candidates = new ArrayList<>();
        for (QuizCandidate candidate : passportRepository.findQuizEligibleCandidates()) {
            if (candidate.usable()) {
                candidates.add(candidate);
            }
        }
        candidates.sort(CANDIDATE_ORDER);

        Map<String, QuizCandidate> uniqueDocuments = new LinkedHashMap<>();
        for (QuizCandidate candidate : candidates) {
            uniqueDocuments.putIfAbsent(candidate.documentId(), candidate);
        }
        return List.copyOf(uniqueDocuments.values());
    }

    private static final class EntityAccumulator {

        private final String entityId;
        private final String displayName;
        private int documentCount;

        private EntityAccumulator(String entityId, String displayName) {
            this.entityId = entityId;
            this.displayName = displayName;
        }
    }
}
