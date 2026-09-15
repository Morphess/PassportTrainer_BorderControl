package ua.passporttrainer.map;

import java.util.Objects;

/** Canonical entity that remains navigable through the fallback list without a unique shape. */
public record MapEntityExclusion(String canonicalEntityId, String reasonCode) {

    public MapEntityExclusion {
        canonicalEntityId = requireText(canonicalEntityId, "canonicalEntityId");
        reasonCode = requireText(reasonCode, "reasonCode");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
