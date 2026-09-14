package ua.passporttrainer.model;

/**
 * Metadata of an image belonging to a passport document.
 */
public record PassportImage(
        long id,
        long passportDocumentId,
        String relativePath,
        String originalFileName,
        String mimeType,
        int width,
        int height,
        long fileSize,
        String sha256
) {
}
