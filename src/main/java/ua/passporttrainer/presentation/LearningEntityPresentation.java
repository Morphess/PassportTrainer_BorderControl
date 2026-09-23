package ua.passporttrainer.presentation;

import java.util.Objects;

/** Presentation-safe summary of one entity with reviewed Learning material. */
public record LearningEntityPresentation(
        String entityId,
        String displayName,
        int documentCount
) {

    public LearningEntityPresentation {
        entityId = requireText(entityId, "entityId");
        displayName = requireText(displayName, "displayName");
        if (documentCount <= 0) {
            throw new IllegalArgumentException("documentCount must be positive");
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
