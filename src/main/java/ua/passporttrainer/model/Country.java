package ua.passporttrainer.model;

/**
 * A country or other passport-issuing entity.
 */
public record Country(
        String id,
        String continentId,
        String isoAlpha2,
        String isoAlpha3,
        String displayName,
        String sourceName,
        EntityType entityType,
        ReviewStatus reviewStatus
) {
}
