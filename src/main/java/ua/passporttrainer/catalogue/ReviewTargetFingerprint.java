package ua.passporttrainer.catalogue;

import ua.passporttrainer.manifest.ManifestCatalogue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Versioned fingerprints for the exact canonical state inspected by a reviewer. */
final class ReviewTargetFingerprint {

    static final int SCHEMA_VERSION = 1;

    private static final String NAMESPACE = "passport-trainer-review-target";

    private ReviewTargetFingerprint() {
    }

    static String entity(ManifestCatalogue.EntityEntry entity) {
        return hash(writer -> {
            writer.text(NAMESPACE);
            writer.integer(SCHEMA_VERSION);
            writer.text("ENTITY");
            writer.text(entity.id());
            writer.text(entity.canonicalKey());
            writer.text(entity.entityType().name());
            writer.text(entity.displayNameUk());
            writer.text(entity.primaryContinentId());
            writer.texts(sorted(entity.alternateContinentIds()));
            writer.texts(sorted(entity.sourceNames()));
            writer.text(entity.reviewStatus().name());
        });
    }

    static String document(ManifestCatalogue.DocumentEntry document) {
        return hash(writer -> {
            writer.text(NAMESPACE);
            writer.integer(SCHEMA_VERSION);
            writer.text("DOCUMENT");
            writer.text(document.id());
            writer.text(document.entityId());
            writer.text(document.documentType().name());
            writer.text(document.titleUk());
            writer.text(document.variant());
            writer.text(document.reviewStatus().name());
            writer.bool(document.quizEligible());
            writer.text(document.duplicateOfDocumentId());
        });
    }

    static String image(ManifestCatalogue.ImageEntry image) {
        return hash(writer -> {
            writer.text(NAMESPACE);
            writer.integer(SCHEMA_VERSION);
            writer.text("IMAGE");
            writer.text(image.id());
            writer.text(image.documentId());
            writer.text(image.sourcePath());
            writer.text(image.sourceFilename());
            writer.text(image.mimeType());
            writer.text(image.format());
            writer.integer(image.width());
            writer.integer(image.height());
            writer.longValue(image.fileSize());
            writer.text(image.sha256());
            writer.text(image.reviewStatus().name());
            writer.text(image.duplicateOfImageId());
        });
    }

    private static List<String> sorted(List<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        return List.copyOf(sorted);
    }

    private static String hash(WriteOperation operation) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                operation.write(new CanonicalWriter(output));
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot serialize review target fingerprint", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    @FunctionalInterface
    private interface WriteOperation {
        void write(CanonicalWriter writer) throws IOException;
    }

    private record CanonicalWriter(DataOutputStream output) {

        private void text(String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.writeInt(bytes.length);
            output.write(bytes);
        }

        private void texts(List<String> values) throws IOException {
            output.writeInt(values.size());
            for (String value : values) {
                text(value);
            }
        }

        private void integer(int value) throws IOException {
            output.writeInt(value);
        }

        private void longValue(long value) throws IOException {
            output.writeLong(value);
        }

        private void bool(boolean value) throws IOException {
            output.writeBoolean(value);
        }
    }
}
