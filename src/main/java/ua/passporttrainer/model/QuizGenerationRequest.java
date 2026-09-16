package ua.passporttrainer.model;

import java.util.Objects;
import java.util.Set;

/** Immutable, UI-independent configuration for one generated quiz session. */
public record QuizGenerationRequest(
        long sessionId,
        long userId,
        QuizMode mode,
        int questionCount,
        int optionCount,
        Set<QuizQuestionType> allowedQuestionTypes,
        Set<String> continentIds,
        Set<String> entityIds,
        Set<DocumentType> documentTypes,
        long randomSeed
) {

    public QuizGenerationRequest {
        mode = Objects.requireNonNull(mode, "mode");
        allowedQuestionTypes = Set.copyOf(Objects.requireNonNull(
                allowedQuestionTypes, "allowedQuestionTypes"
        ));
        continentIds = Set.copyOf(Objects.requireNonNull(continentIds, "continentIds"));
        entityIds = Set.copyOf(Objects.requireNonNull(entityIds, "entityIds"));
        documentTypes = Set.copyOf(Objects.requireNonNull(documentTypes, "documentTypes"));
    }
}
