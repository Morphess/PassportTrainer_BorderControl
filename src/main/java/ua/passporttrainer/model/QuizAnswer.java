package ua.passporttrainer.model;

import java.time.Instant;

/**
 * A user's answer to one quiz question.
 */
public record QuizAnswer(
        long questionId,
        String selectedAnswer,
        boolean correct,
        Instant answeredAt
) {
}
