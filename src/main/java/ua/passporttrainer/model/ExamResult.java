package ua.passporttrainer.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistable summary of a completed examination session.
 */
public record ExamResult(
        long id,
        Long userId,
        long quizSessionId,
        AssessmentSource assessmentSource,
        Optional<AssessmentProvenance> assessmentProvenance,
        Instant startedAt,
        Instant completedAt,
        int correctAnswers,
        int totalQuestions
) {
    public ExamResult {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("userId must be positive when present");
        }
        if (quizSessionId <= 0) {
            throw new IllegalArgumentException("quizSessionId must be positive");
        }
        assessmentSource = Objects.requireNonNull(assessmentSource, "assessmentSource");
        assessmentProvenance = Objects.requireNonNull(
                assessmentProvenance, "assessmentProvenance"
        );
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (assessmentSource != AssessmentSource.GENERATED
                && assessmentProvenance.isEmpty()) {
            throw new IllegalArgumentException(
                    "Authored or hybrid results require assessment provenance"
            );
        }
        if (correctAnswers < 0) {
            throw new IllegalArgumentException("correctAnswers must not be negative");
        }
        if (totalQuestions <= 0) {
            throw new IllegalArgumentException("totalQuestions must be positive");
        }
        if (correctAnswers > totalQuestions) {
            throw new IllegalArgumentException("correctAnswers must not exceed totalQuestions");
        }
    }

    public int incorrectAnswers() {
        return totalQuestions - correctAnswers;
    }

    public double scorePercent() {
        return correctAnswers * 100.0 / totalQuestions;
    }

    public long durationSeconds() {
        return Math.max(0, Duration.between(startedAt, completedAt).toSeconds());
    }
}
