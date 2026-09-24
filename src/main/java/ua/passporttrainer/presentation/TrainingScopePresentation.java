package ua.passporttrainer.presentation;

import java.util.Objects;
import java.util.Optional;

/** Typed selectable Training scope; an empty entity ID represents the global eligible pool. */
public record TrainingScopePresentation(
        Optional<String> entityId,
        String displayName,
        int documentCount
) {

    public TrainingScopePresentation {
        entityId = Objects.requireNonNull(entityId, "entityId");
        entityId.ifPresent(id -> requireText(id, "entityId"));
        displayName = requireText(displayName, "displayName");
        if (documentCount < 0) {
            throw new IllegalArgumentException("documentCount must not be negative");
        }
    }

    public static TrainingScopePresentation all(String displayName, int documentCount) {
        return new TrainingScopePresentation(Optional.empty(), displayName, documentCount);
    }

    public static TrainingScopePresentation entity(
            String entityId,
            String displayName,
            int documentCount
    ) {
        return new TrainingScopePresentation(
                Optional.of(requireText(entityId, "entityId")),
                displayName,
                documentCount
        );
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
