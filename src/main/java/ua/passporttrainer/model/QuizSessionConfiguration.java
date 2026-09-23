package ua.passporttrainer.model;

import java.util.Objects;
import java.util.Set;

/** Accepted, UI-independent configuration used to generate one quiz session. */
public record QuizSessionConfiguration(
        QuizMode mode,
        int questionCount,
        int optionCount,
        Set<QuizQuestionType> allowedQuestionTypes,
        Set<String> continentIds,
        Set<String> entityIds,
        Set<DocumentType> documentTypes
) {

    public QuizSessionConfiguration {
        mode = Objects.requireNonNull(mode, "mode");
        allowedQuestionTypes = Set.copyOf(Objects.requireNonNull(
                allowedQuestionTypes, "allowedQuestionTypes"
        ));
        continentIds = Set.copyOf(Objects.requireNonNull(continentIds, "continentIds"));
        entityIds = Set.copyOf(Objects.requireNonNull(entityIds, "entityIds"));
        documentTypes = Set.copyOf(Objects.requireNonNull(documentTypes, "documentTypes"));
    }
}
