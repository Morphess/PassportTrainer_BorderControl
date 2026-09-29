package ua.passporttrainer.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Minimal authored assessment aggregate; builder/version policies are intentionally deferred. */
public record AssessmentDefinition(
        String assessmentId,
        AssessmentState state,
        String title,
        Optional<String> description,
        long createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public AssessmentDefinition {
        assessmentId = requireText(assessmentId, "assessmentId");
        state = Objects.requireNonNull(state, "state");
        title = requireText(title, "title");
        description = Objects.requireNonNull(description, "description");
        description.ifPresent(value -> requireText(value, "description"));
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
