package ua.passporttrainer.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Metadata only; upload, copy, and catalogue resolution belong to future services. */
public record MediaMetadata(
        String mediaId,
        MediaReferenceType referenceType,
        String reference,
        String mimeType,
        OptionalLong sizeBytes,
        Optional<String> sha256,
        long createdBy,
        Instant createdAt,
        Instant updatedAt,
        boolean archived
) {
    public MediaMetadata {
        mediaId = requireText(mediaId, "mediaId");
        referenceType = Objects.requireNonNull(referenceType, "referenceType");
        reference = requireText(reference, "reference");
        mimeType = requireText(mimeType, "mimeType");
        sizeBytes = Objects.requireNonNull(sizeBytes, "sizeBytes");
        if (sizeBytes.isPresent() && sizeBytes.getAsLong() < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        sha256 = Objects.requireNonNull(sha256, "sha256");
        sha256.ifPresent(MediaMetadata::requireSha256);
        if (createdBy <= 0) {
            throw new IllegalArgumentException("createdBy must be positive");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }

    private static void requireSha256(String value) {
        if (value.length() != 64) {
            throw new IllegalArgumentException("sha256 must contain 64 lowercase hexadecimal characters");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) {
                throw new IllegalArgumentException(
                        "sha256 must contain 64 lowercase hexadecimal characters"
                );
            }
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
