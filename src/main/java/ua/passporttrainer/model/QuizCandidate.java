package ua.passporttrainer.model;

import java.util.Objects;
import java.util.Set;

/** Canonical document/image candidate returned by the quiz repository query. */
public record QuizCandidate(
        String documentId,
        String entityId,
        String entityDisplayName,
        Set<String> continentIds,
        DocumentType documentType,
        String documentTitle,
        String imageId,
        String imageRelativePath,
        String imageSha256,
        ReviewStatus entityReviewStatus,
        ReviewStatus documentReviewStatus,
        ReviewStatus imageReviewStatus,
        boolean quizEligible,
        String canonicalDocumentId,
        String canonicalImageId
) {

    public QuizCandidate {
        documentId = requireText(documentId, "documentId");
        entityId = requireText(entityId, "entityId");
        entityDisplayName = requireText(entityDisplayName, "entityDisplayName");
        continentIds = Set.copyOf(Objects.requireNonNull(continentIds, "continentIds"));
        if (continentIds.isEmpty() || continentIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("continentIds must contain nonblank values");
        }
        documentType = Objects.requireNonNull(documentType, "documentType");
        documentTitle = requireText(documentTitle, "documentTitle");
        imageId = requireText(imageId, "imageId");
        imageRelativePath = requireText(imageRelativePath, "imageRelativePath");
        imageSha256 = requireText(imageSha256, "imageSha256");
        if (!isLowercaseSha256(imageSha256)) {
            throw new IllegalArgumentException("imageSha256 must be lowercase SHA-256");
        }
        entityReviewStatus = Objects.requireNonNull(entityReviewStatus, "entityReviewStatus");
        documentReviewStatus = Objects.requireNonNull(documentReviewStatus, "documentReviewStatus");
        imageReviewStatus = Objects.requireNonNull(imageReviewStatus, "imageReviewStatus");
        canonicalDocumentId = requireText(canonicalDocumentId, "canonicalDocumentId");
        canonicalImageId = requireText(canonicalImageId, "canonicalImageId");
    }

    public boolean usable() {
        return quizEligible
                && entityReviewStatus == ReviewStatus.VERIFIED
                && documentReviewStatus == ReviewStatus.VERIFIED
                && imageReviewStatus == ReviewStatus.VERIFIED
                && documentType != DocumentType.UNKNOWN
                && documentId.equals(canonicalDocumentId)
                && imageId.equals(canonicalImageId);
    }

    public QuizContent.Document documentContent() {
        return new QuizContent.Document(
                documentId,
                entityId,
                documentType,
                documentTitle,
                imageId,
                imageRelativePath
        );
    }

    private static boolean isLowercaseSha256(String value) {
        if (value.length() != 64) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
