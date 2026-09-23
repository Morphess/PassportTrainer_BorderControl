package ua.passporttrainer.service;

/** Derived progress of one active or completed quiz session. */
public record QuizSessionProgress(
        int currentQuestionNumber,
        int totalQuestions,
        int answeredCount,
        int remainingCount,
        boolean completionReady
) {

    public QuizSessionProgress {
        if (totalQuestions <= 0) {
            throw new IllegalArgumentException("totalQuestions must be positive");
        }
        if (currentQuestionNumber <= 0 || currentQuestionNumber > totalQuestions) {
            throw new IllegalArgumentException("currentQuestionNumber is outside the session");
        }
        if (answeredCount < 0 || answeredCount > totalQuestions) {
            throw new IllegalArgumentException("answeredCount is outside the session");
        }
        if (remainingCount != totalQuestions - answeredCount) {
            throw new IllegalArgumentException("remainingCount does not match answeredCount");
        }
        if (completionReady != (remainingCount == 0)) {
            throw new IllegalArgumentException("completionReady must match remainingCount");
        }
    }
}
