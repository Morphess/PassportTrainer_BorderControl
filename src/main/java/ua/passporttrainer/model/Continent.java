package ua.passporttrainer.model;

/**
 * A continent available in the application catalogue.
 */
public record Continent(
        long id,
        String code,
        String displayName,
        String sourceName,
        int sortOrder
) {
}
