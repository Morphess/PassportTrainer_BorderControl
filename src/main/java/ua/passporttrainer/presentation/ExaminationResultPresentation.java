package ua.passporttrainer.presentation;

import java.time.Duration;
import java.util.Objects;

import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizSession;

/** Derived, non-persisted result for one completed Examination session. */
public record ExaminationResultPresentation(
        int correctCount,
        int incorrectCount,
        int totalCount,
        int percentage,
        long durationSeconds
) {

    public ExaminationResultPresentation {
        if (totalCount <= 0) {
            throw new IllegalArgumentException("totalCount must be positive");
        }
        if (correctCount < 0 || correctCount > totalCount) {
            throw new IllegalArgumentException("correctCount is outside the result");
        }
        if (incorrectCount != totalCount - correctCount) {
            throw new IllegalArgumentException("incorrectCount does not match the total");
        }
        int expectedPercentage = (int) Math.round(correctCount * 100.0 / totalCount);
        if (percentage != expectedPercentage) {
            throw new IllegalArgumentException("percentage does not match the counts");
        }
        if (durationSeconds < 0) {
            throw new IllegalArgumentException("durationSeconds must not be negative");
        }
    }

    public static ExaminationResultPresentation fromCompletedSession(
            QuizSession session,
            int correctCount
    ) {
        Objects.requireNonNull(session, "session");
        if (session.mode() != QuizMode.EXAMINATION) {
            throw new IllegalArgumentException("Session is not an examination");
        }
        if (!session.completed()) {
            throw new IllegalStateException("Examination is not completed");
        }
        if (session.answers().size() != session.questions().size()) {
            throw new IllegalStateException("Examination contains unanswered questions");
        }
        int totalCount = session.questions().size();
        long durationSeconds = Math.max(
                0L,
                Duration.between(session.startedAt(), session.completedAt()).toSeconds()
        );
        return new ExaminationResultPresentation(
                correctCount,
                totalCount - correctCount,
                totalCount,
                (int) Math.round(correctCount * 100.0 / totalCount),
                durationSeconds
        );
    }
}
