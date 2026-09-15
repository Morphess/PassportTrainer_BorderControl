package ua.passporttrainer.map;

import java.util.List;
import java.util.Objects;

/** Presentation-level grouping of one or more canonical continents. */
public record MapGroup(
        String id,
        String labelKey,
        List<String> canonicalContinentIds,
        boolean wrapsDateline,
        MapBounds focusBounds
) {
    public MapGroup {
        id = requireText(id, "id");
        labelKey = requireText(labelKey, "labelKey");
        canonicalContinentIds = List.copyOf(Objects.requireNonNull(
                canonicalContinentIds, "canonicalContinentIds"
        ));
        if (canonicalContinentIds.isEmpty()
                || canonicalContinentIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Map group must reference canonical continents");
        }
        focusBounds = Objects.requireNonNull(focusBounds, "focusBounds");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
