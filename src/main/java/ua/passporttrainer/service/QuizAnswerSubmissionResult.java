package ua.passporttrainer.service;

import java.util.Objects;

/** Mode-safe result of accepting one answer through the session coordinator. */
public sealed interface QuizAnswerSubmissionResult permits
        QuizAnswerSubmissionResult.Accepted,
        QuizAnswerSubmissionResult.Feedback {

    String acceptedAnswerId();

    int answeredCount();

    int remainingCount();

    boolean completionReady();

    record Accepted(
            String acceptedAnswerId,
            int answeredCount,
            int remainingCount,
            boolean completionReady
    ) implements QuizAnswerSubmissionResult {

        public Accepted {
            acceptedAnswerId = validate(acceptedAnswerId, answeredCount, remainingCount, completionReady);
        }
    }

    record Feedback(
            String acceptedAnswerId,
            boolean correct,
            String correctOptionId,
            int answeredCount,
            int remainingCount,
            boolean completionReady
    ) implements QuizAnswerSubmissionResult {

        public Feedback {
            acceptedAnswerId = validate(acceptedAnswerId, answeredCount, remainingCount, completionReady);
            correctOptionId = requireText(correctOptionId, "correctOptionId");
        }
    }

    private static String validate(
            String acceptedAnswerId,
            int answeredCount,
            int remainingCount,
            boolean completionReady
    ) {
        if (answeredCount < 0 || remainingCount < 0) {
            throw new IllegalArgumentException("answer counts must not be negative");
        }
        if (completionReady != (remainingCount == 0)) {
            throw new IllegalArgumentException("completionReady must match remainingCount");
        }
        return requireText(acceptedAnswerId, "acceptedAnswerId");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
