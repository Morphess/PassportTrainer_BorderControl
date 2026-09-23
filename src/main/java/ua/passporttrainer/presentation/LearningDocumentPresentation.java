package ua.passporttrainer.presentation;

import java.util.Objects;

/** Presentation-safe reviewed document metadata for passive Learning browsing. */
public record LearningDocumentPresentation(
        String entityId,
        String entityDisplayName,
        String documentId,
        String imageId,
        String imageRelativePath,
        String documentTypeLabelKey,
        String title,
        String variant
) {

    public LearningDocumentPresentation {
        entityId = requireText(entityId, "entityId");
        entityDisplayName = requireText(entityDisplayName, "entityDisplayName");
        documentId = requireText(documentId, "documentId");
        imageId = requireText(imageId, "imageId");
        imageRelativePath = requireText(imageRelativePath, "imageRelativePath");
        documentTypeLabelKey = requireText(documentTypeLabelKey, "documentTypeLabelKey");
        title = requireText(title, "title");
        variant = variant == null ? "" : variant.strip();
    }

    public boolean hasMeaningfulVariant() {
        return !variant.isBlank();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
