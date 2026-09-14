package ua.passporttrainer.model;

import java.util.List;

/**
 * A multiple-choice question generated for a passport document.
 */
public record QuizQuestion(
        long id,
        long passportDocumentId,
        String prompt,
        List<String> options,
        String correctAnswer
) {
    public QuizQuestion {
        options = List.copyOf(options);
    }
}
