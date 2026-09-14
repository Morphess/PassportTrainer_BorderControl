package ua.passporttrainer.model;

/**
 * A continent available in the application catalogue.
 */
public record Continent(
        String id,
        String code,
        String displayName,
        String sourceName,
        int sortOrder
) {
}
