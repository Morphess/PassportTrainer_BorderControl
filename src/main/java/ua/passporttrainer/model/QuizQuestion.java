package ua.passporttrainer.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

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
        prompt = Objects.requireNonNull(prompt, "prompt");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        correctAnswer = Objects.requireNonNull(correctAnswer, "correctAnswer");

        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (options.size() < 2) {
            throw new IllegalArgumentException("options must contain at least two values");
        }
        if (new HashSet<>(options).size() != options.size()) {
            throw new IllegalArgumentException("options must be unique");
        }
        if (!options.contains(correctAnswer)) {
            throw new IllegalArgumentException("correctAnswer must be one of options");
        }
    }
}
