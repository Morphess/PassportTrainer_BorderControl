package ua.passporttrainer.model;

/**
 * A logical passport document or document variant.
 */
public record PassportDocument(
        String id,
        String countryId,
        DocumentType documentType,
        String title,
        String variant,
        String sourceRelativePath,
        ReviewStatus reviewStatus,
        int imageCount
) {
}
