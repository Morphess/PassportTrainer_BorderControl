package ua.passporttrainer.model;

/**
 * A logical passport document or document variant.
 */
public record PassportDocument(
        long id,
        long countryId,
        DocumentType documentType,
        String title,
        String variant,
        String sourceRelativePath
) {
}
