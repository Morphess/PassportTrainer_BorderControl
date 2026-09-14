package ua.passporttrainer.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistable summary of a completed examination session.
 */
public record ExamResult(
        long id,
        long userId,
        long quizSessionId,
        int correctAnswers,
        int totalQuestions,
        long durationSeconds,
        Instant completedAt
) {
    public ExamResult {
        if (correctAnswers < 0) {
            throw new IllegalArgumentException("correctAnswers must not be negative");
        }
        if (totalQuestions <= 0) {
            throw new IllegalArgumentException("totalQuestions must be positive");
        }
        if (correctAnswers > totalQuestions) {
            throw new IllegalArgumentException("correctAnswers must not exceed totalQuestions");
        }
        if (durationSeconds < 0) {
            throw new IllegalArgumentException("durationSeconds must not be negative");
        }
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
    }

    public double scorePercent() {
        return correctAnswers * 100.0 / totalQuestions;
    }
}
