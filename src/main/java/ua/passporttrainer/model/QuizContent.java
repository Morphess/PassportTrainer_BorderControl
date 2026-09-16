package ua.passporttrainer.model;

import java.util.Objects;

/** Stable subject or answer content carried by a generated quiz question. */
public sealed interface QuizContent permits
        QuizContent.Country,
        QuizContent.Document,
        QuizContent.DocumentTypeValue {

    String id();

    record Country(String id, String displayName) implements QuizContent {

        public Country {
            id = requireText(id, "id");
            displayName = requireText(displayName, "displayName");
        }
    }

    record Document(
            String id,
            String entityId,
            DocumentType documentType,
            String title,
            String imageId,
            String imageRelativePath
    ) implements QuizContent {

        public Document {
            id = requireText(id, "id");
            entityId = requireText(entityId, "entityId");
            documentType = Objects.requireNonNull(documentType, "documentType");
            if (documentType == DocumentType.UNKNOWN) {
                throw new IllegalArgumentException("documentType must be known");
            }
            title = requireText(title, "title");
            imageId = requireText(imageId, "imageId");
            imageRelativePath = requireText(imageRelativePath, "imageRelativePath");
        }
    }

    record DocumentTypeValue(DocumentType documentType) implements QuizContent {

        public DocumentTypeValue {
            documentType = Objects.requireNonNull(documentType, "documentType");
            if (documentType == DocumentType.UNKNOWN) {
                throw new IllegalArgumentException("documentType must be known");
            }
        }

        @Override
        public String id() {
            return documentType.name();
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
