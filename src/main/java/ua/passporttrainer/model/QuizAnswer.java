package ua.passporttrainer.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A user's answer to one quiz question.
 */
public record QuizAnswer(
        long questionId,
        String selectedAnswer,
        boolean correct,
        Instant answeredAt
) {
    public QuizAnswer {
        selectedAnswer = Objects.requireNonNull(selectedAnswer, "selectedAnswer");
        answeredAt = Objects.requireNonNull(answeredAt, "answeredAt");
    }
}
