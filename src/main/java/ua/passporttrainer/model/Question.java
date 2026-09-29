package ua.passporttrainer.model;

import java.time.Instant;
import java.util.Objects;

/** Editable aggregate identity and lifecycle; not a runtime {@link QuizQuestion}. */
public record Question(
        String questionId,
        QuestionLifecycleState lifecycleState,
        long createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public Question {
        questionId = requireText(questionId, "questionId");
        lifecycleState = Objects.requireNonNull(lifecycleState, "lifecycleState");
        if (createdBy <= 0) {
            throw new IllegalArgumentException("createdBy must be positive");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
