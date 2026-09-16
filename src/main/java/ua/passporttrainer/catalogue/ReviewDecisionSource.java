package ua.passporttrainer.catalogue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Canonical, version-controlled source of explicit human review and quiz activation decisions. */
final class ReviewDecisionSource {

    static final int SCHEMA_VERSION = 1;
    static final String FILE_NAME = "review-decisions.tsv";
    static final String HEADER = String.join("\t",
            "recordType", "targetType", "targetId", "targetFingerprintSha256",
            "decision", "reviewer", "decidedAt", "note");

    private static final Pattern LOWERCASE_SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Map<TargetType, Pattern> STABLE_IDS = stableIdPatterns();
    private static final Comparator<DecisionRecord> ORDER = Comparator
            .comparing(DecisionRecord::recordType)
            .thenComparing(DecisionRecord::targetType)
            .thenComparing(DecisionRecord::targetId);

    private final List<DecisionRecord> records;
    private final byte[] canonicalBytes;
    private final String checksum;

    private ReviewDecisionSource(List<DecisionRecord> records) {
        List<DecisionRecord> ordered = new ArrayList<>(records);
        ordered.sort(ORDER);
        requireUniqueKeys(ordered);
        this.records = List.copyOf(ordered);
        this.canonicalBytes = serialize(this.records);
        this.checksum = sha256(canonicalBytes);
    }

    static ReviewDecisionSource empty() {
        return new ReviewDecisionSource(List.of());
    }

    static ReviewDecisionSource of(List<DecisionRecord> records) {
        Objects.requireNonNull(records, "records");
        for (DecisionRecord record : records) {
            validateRecord(record);
        }
        return new ReviewDecisionSource(records);
    }

    static ReviewDecisionSource read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes = Files.readAllBytes(path);
        String text = decodeUtf8(bytes, path);
        if (text.indexOf('\r') >= 0) {
            throw invalid("Review source must use LF line endings: " + path);
        }
        if (!text.endsWith("\n")) {
            throw invalid("Review source must end with LF: " + path);
        }
        String[] lines = text.split("\n", -1);
        if (lines.length < 2 || !lines[0].equals(HEADER)) {
            throw invalid("Unexpected review TSV header: " + path);
        }
        List<DecisionRecord> records = new ArrayList<>();
        for (int index = 1; index < lines.length - 1; index++) {
            String line = lines[index];
            if (line.isEmpty()) {
                throw invalid("Blank review TSV row at " + path + ':' + (index + 1));
            }
            String[] values = line.split("\t", -1);
            if (values.length != 8) {
                throw invalid("Wrong review TSV column count at " + path + ':' + (index + 1));
            }
            records.add(parseRecord(values, path, index + 1));
        }
        ReviewDecisionSource source = of(records);
        if (!MessageDigest.isEqual(bytes, source.canonicalBytes)) {
            throw invalid("Review source is not in canonical deterministic form: " + path);
        }
        return source;
    }

    List<DecisionRecord> records() {
        return records;
    }

    String checksum() {
        return checksum;
    }

    byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    ReviewDecisionSource replacing(DecisionRecord replacement) {
        validateRecord(replacement);
        List<DecisionRecord> updated = new ArrayList<>(records.size() + 1);
        boolean replaced = false;
        for (DecisionRecord record : records) {
            if (record.key().equals(replacement.key())) {
                updated.add(replacement);
                replaced = true;
            } else {
                updated.add(record);
            }
        }
        if (!replaced) {
            updated.add(replacement);
        }
        return of(updated);
    }

    DecisionRecord find(RecordType recordType, TargetType targetType, String targetId) {
        DecisionKey key = new DecisionKey(recordType, targetType, targetId);
        for (DecisionRecord record : records) {
            if (record.key().equals(key)) {
                return record;
            }
        }
        return null;
    }

    static void writeAtomically(
            Path path,
            String expectedChecksum,
            ReviewDecisionSource replacement
    ) throws IOException {
        writeAtomically(path, expectedChecksum, replacement, Files::move);
    }

    static void writeAtomically(
            Path path,
            String expectedChecksum,
            ReviewDecisionSource replacement,
            AtomicMover mover
    ) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(expectedChecksum, "expectedChecksum");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(mover, "mover");

        ReviewDecisionSource current = read(path);
        if (!current.checksum.equals(expectedChecksum)) {
            throw new ReviewValidationException(
                    ReviewFailureReason.CONFLICTING_DECISION,
                    "",
                    "Review source changed since it was read"
            );
        }

        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw invalid("Review source must have a parent directory");
        }
        Path temporary = Files.createTempFile(parent, path.getFileName() + ".", ".tmp");
        boolean replaced = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer bytes = ByteBuffer.wrap(replacement.canonicalBytes);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            mover.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static DecisionRecord parseRecord(String[] values, Path path, int lineNumber) {
        try {
            RecordType recordType = RecordType.valueOf(values[0]);
            TargetType targetType = TargetType.valueOf(values[1]);
            Decision decision = Decision.valueOf(values[4]);
            Instant instant = Instant.parse(values[6]);
            if (!instant.toString().equals(values[6])) {
                throw invalid("Timestamp is not canonical UTC at " + path + ':' + lineNumber);
            }
            DecisionRecord record = new DecisionRecord(
                    recordType,
                    targetType,
                    values[2],
                    values[3],
                    decision,
                    values[5],
                    instant,
                    values[7]
            );
            validateRecord(record);
            return record;
        } catch (IllegalArgumentException | DateTimeException exception) {
            if (exception instanceof ReviewValidationException reviewException) {
                throw reviewException;
            }
            throw new ReviewValidationException(
                    ReviewFailureReason.INVALID_REVIEW_SOURCE,
                    values[2],
                    "Invalid review TSV row at " + path + ':' + lineNumber,
                    exception
            );
        }
    }

    private static void validateRecord(DecisionRecord record) {
        Objects.requireNonNull(record, "record");
        if (!STABLE_IDS.get(record.targetType()).matcher(record.targetId()).matches()) {
            throw invalid(record.targetId(), "Invalid stable target ID for " + record.targetType());
        }
        if (!LOWERCASE_SHA256.matcher(record.targetFingerprintSha256()).matches()) {
            throw invalid(record.targetId(), "Invalid target fingerprint SHA-256");
        }
        boolean validCombination = switch (record.recordType()) {
            case REVIEW -> record.decision() == Decision.VERIFIED
                    || record.decision() == Decision.NEEDS_REVIEW
                    || record.decision() == Decision.EXCLUDED;
            case QUIZ_ACTIVATION -> record.targetType() == TargetType.DOCUMENT
                    && (record.decision() == Decision.ENABLED || record.decision() == Decision.DISABLED);
        };
        if (!validCombination) {
            ReviewFailureReason reason = record.recordType() == RecordType.QUIZ_ACTIVATION
                    ? ReviewFailureReason.ACTIVATION_NOT_ALLOWED
                    : ReviewFailureReason.CONFLICTING_DECISION;
            throw new ReviewValidationException(
                    reason,
                    record.targetId(),
                    "Decision is not valid for record/target type"
            );
        }
        String reviewer = record.reviewer();
        if (reviewer.isBlank() || !reviewer.equals(reviewer.strip()) || reviewer.length() > 128
                || reviewer.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid(record.targetId(), "Invalid reviewer identifier");
        }
        String note = record.note();
        if (!note.equals(note.strip()) || note.length() > 2_000
                || note.indexOf('\t') >= 0 || note.indexOf('\n') >= 0 || note.indexOf('\r') >= 0) {
            throw invalid(record.targetId(), "Invalid review note");
        }
        if ((record.decision() == Decision.NEEDS_REVIEW
                || record.decision() == Decision.EXCLUDED
                || record.decision() == Decision.DISABLED) && note.isBlank()) {
            throw invalid(record.targetId(), "This decision requires a nonblank note");
        }
    }

    private static byte[] serialize(List<DecisionRecord> records) {
        StringBuilder output = new StringBuilder(HEADER).append('\n');
        for (DecisionRecord record : records) {
            append(output, record.recordType().name());
            append(output, record.targetType().name());
            append(output, record.targetId());
            append(output, record.targetFingerprintSha256());
            append(output, record.decision().name());
            append(output, record.reviewer());
            append(output, record.decidedAt().toString());
            output.append(record.note()).append('\n');
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void append(StringBuilder output, String value) {
        if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw invalid("Review TSV values must not contain tabs or line breaks");
        }
        output.append(value).append('\t');
    }

    private static void requireUniqueKeys(List<DecisionRecord> records) {
        Set<DecisionKey> keys = new HashSet<>();
        for (DecisionRecord record : records) {
            if (!keys.add(record.key())) {
                throw new ReviewValidationException(
                        ReviewFailureReason.DUPLICATE_DECISION,
                        record.targetId(),
                        "Duplicate review decision key: " + record.key()
                );
            }
        }
    }

    private static String decodeUtf8(byte[] bytes, Path path) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            throw invalid("Review source must not contain a UTF-8 BOM: " + path);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ReviewValidationException(
                    ReviewFailureReason.INVALID_REVIEW_SOURCE,
                    "",
                    "Review source is not valid UTF-8: " + path,
                    exception
            );
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static Map<TargetType, Pattern> stableIdPatterns() {
        Map<TargetType, Pattern> patterns = new EnumMap<>(TargetType.class);
        patterns.put(TargetType.ENTITY, Pattern.compile("entity:[0-9a-f]{64}"));
        patterns.put(TargetType.DOCUMENT, Pattern.compile("document:[0-9a-f]{64}"));
        patterns.put(TargetType.IMAGE, Pattern.compile("image:[0-9a-f]{64}"));
        return Map.copyOf(patterns);
    }

    private static ReviewValidationException invalid(String message) {
        return invalid("", message);
    }

    private static ReviewValidationException invalid(String targetId, String message) {
        return new ReviewValidationException(ReviewFailureReason.INVALID_REVIEW_SOURCE, targetId, message);
    }

    enum RecordType {
        REVIEW,
        QUIZ_ACTIVATION
    }

    enum TargetType {
        ENTITY,
        DOCUMENT,
        IMAGE
    }

    enum Decision {
        VERIFIED,
        NEEDS_REVIEW,
        EXCLUDED,
        ENABLED,
        DISABLED
    }

    record DecisionKey(RecordType recordType, TargetType targetType, String targetId) {
    }

    record DecisionRecord(
            RecordType recordType,
            TargetType targetType,
            String targetId,
            String targetFingerprintSha256,
            Decision decision,
            String reviewer,
            Instant decidedAt,
            String note
    ) {
        DecisionRecord {
            Objects.requireNonNull(recordType, "recordType");
            Objects.requireNonNull(targetType, "targetType");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(targetFingerprintSha256, "targetFingerprintSha256");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(reviewer, "reviewer");
            Objects.requireNonNull(decidedAt, "decidedAt");
            Objects.requireNonNull(note, "note");
        }

        DecisionKey key() {
            return new DecisionKey(recordType, targetType, targetId);
        }
    }

    @FunctionalInterface
    interface AtomicMover {
        Path move(Path source, Path target, java.nio.file.CopyOption... options) throws IOException;
    }
}
