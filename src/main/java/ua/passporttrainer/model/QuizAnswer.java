package ua.passporttrainer.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A user's answer to one quiz question.
 */
public record QuizAnswer(
        long questionId,
        String selectedAnswerId,
        boolean correct,
        Instant answeredAt
) {
    public QuizAnswer {
        selectedAnswerId = Objects.requireNonNull(selectedAnswerId, "selectedAnswerId");
        if (selectedAnswerId.isBlank()) {
            throw new IllegalArgumentException("selectedAnswerId must not be blank");
        }
        answeredAt = Objects.requireNonNull(answeredAt, "answeredAt");
    }
}
