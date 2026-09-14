package ua.passporttrainer.model;

import java.time.Instant;

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
    public double scorePercent() {
        return totalQuestions == 0 ? 0.0 : correctAnswers * 100.0 / totalQuestions;
    }
}
