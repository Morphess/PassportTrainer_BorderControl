package ua.passporttrainer.catalogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewDecisionSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void checkedInSourceIsCanonicalHeaderOnlyUtf8() throws Exception {
        Path path = Path.of("data", "passport-review", ReviewDecisionSource.FILE_NAME);
        ReviewDecisionSource source = ReviewDecisionSource.read(path);

        assertTrue(source.records().isEmpty());
        assertArrayEquals((ReviewDecisionSource.HEADER + "\n").getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(path));
    }

    @Test
    void writerUsesCanonicalLfOrderingAndTimestampRepresentation() throws Exception {
        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource source = ReviewDecisionSource.of(List.of(
                ReviewTestSupport.review(
                        ReviewDecisionSource.TargetType.IMAGE,
                        fixture.image().id(),
                        ReviewTargetFingerprint.image(fixture.image()),
                        ReviewDecisionSource.Decision.VERIFIED
                ),
                ReviewTestSupport.review(
                        ReviewDecisionSource.TargetType.ENTITY,
                        fixture.entity().id(),
                        ReviewTargetFingerprint.entity(fixture.entity()),
                        ReviewDecisionSource.Decision.VERIFIED
                )
        ));
        String output = new String(source.canonicalBytes(), StandardCharsets.UTF_8);

        assertTrue(output.startsWith(ReviewDecisionSource.HEADER + "\nREVIEW\tENTITY\t"));
        assertTrue(output.endsWith("2026-09-16T10:15:30Z\t\n"));
        assertFalse(output.contains("\r"));
    }

    @Test
    void parserRejectsBomCrLfNoncanonicalOrderAndAutomaticDecision() throws Exception {
        Path path = temporaryDirectory.resolve("review.tsv");
        Files.write(path, ("\ufeff" + ReviewDecisionSource.HEADER + "\n").getBytes(StandardCharsets.UTF_8));
        assertReason(ReviewFailureReason.INVALID_REVIEW_SOURCE, () -> ReviewDecisionSource.read(path));

        Files.writeString(path, ReviewDecisionSource.HEADER + "\r\n", StandardCharsets.UTF_8);
        assertReason(ReviewFailureReason.INVALID_REVIEW_SOURCE, () -> ReviewDecisionSource.read(path));

        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource ordered = ReviewDecisionSource.of(List.of(
                ReviewTestSupport.review(ReviewDecisionSource.TargetType.ENTITY, fixture.entity().id(),
                        ReviewTargetFingerprint.entity(fixture.entity()), ReviewDecisionSource.Decision.VERIFIED),
                ReviewTestSupport.review(ReviewDecisionSource.TargetType.IMAGE, fixture.image().id(),
                        ReviewTargetFingerprint.image(fixture.image()), ReviewDecisionSource.Decision.VERIFIED)
        ));
        List<String> lines = new java.util.ArrayList<>(List.of(
                new String(ordered.canonicalBytes(), StandardCharsets.UTF_8).split("\n", -1)
        ));
        String swapped = lines.get(0) + '\n' + lines.get(2) + '\n' + lines.get(1) + '\n';
        Files.writeString(path, swapped, StandardCharsets.UTF_8);
        assertReason(ReviewFailureReason.INVALID_REVIEW_SOURCE, () -> ReviewDecisionSource.read(path));

        String automatic = ReviewDecisionSource.HEADER + "\nREVIEW\tENTITY\t" + fixture.entity().id() + '\t'
                + ReviewTargetFingerprint.entity(fixture.entity())
                + "\tAUTO_CLASSIFIED\treviewer.test\t2026-09-16T10:15:30Z\t\n";
        Files.writeString(path, automatic, StandardCharsets.UTF_8);
        assertReason(ReviewFailureReason.INVALID_REVIEW_SOURCE, () -> ReviewDecisionSource.read(path));
    }

    @Test
    void duplicateLogicalKeyIsRejected() {
        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource.DecisionRecord record = ReviewTestSupport.review(
                ReviewDecisionSource.TargetType.ENTITY,
                fixture.entity().id(),
                ReviewTargetFingerprint.entity(fixture.entity()),
                ReviewDecisionSource.Decision.VERIFIED
        );

        assertReason(ReviewFailureReason.DUPLICATE_DECISION,
                () -> ReviewDecisionSource.of(List.of(record, record)));
    }

    @Test
    void activationIsAllowedOnlyForDocuments() {
        ReviewTestSupport.Fixture fixture = ReviewTestSupport.fixture();
        ReviewDecisionSource.DecisionRecord invalid = new ReviewDecisionSource.DecisionRecord(
                ReviewDecisionSource.RecordType.QUIZ_ACTIVATION,
                ReviewDecisionSource.TargetType.ENTITY,
                fixture.entity().id(),
                ReviewTargetFingerprint.entity(fixture.entity()),
                ReviewDecisionSource.Decision.ENABLED,
                "reviewer.test",
                ReviewTestSupport.DECIDED_AT,
                ""
        );

        assertReason(ReviewFailureReason.ACTIVATION_NOT_ALLOWED,
                () -> ReviewDecisionSource.of(List.of(invalid)));
    }

    @Test
    void failedAtomicReplacementPreservesAuthoritativeFileAndCleansTemporaryFile() throws Exception {
        Path path = temporaryDirectory.resolve(ReviewDecisionSource.FILE_NAME);
        ReviewDecisionSource current = ReviewDecisionSource.empty();
        Files.write(path, current.canonicalBytes());
        ReviewDecisionSource replacement = ReviewTestSupport.verifiedSource(ReviewTestSupport.fixture(), false);
        byte[] before = Files.readAllBytes(path);

        assertThrows(IOException.class, () -> ReviewDecisionSource.writeAtomically(
                path,
                current.checksum(),
                replacement,
                (source, target, options) -> {
                    throw new IOException("forced atomic move failure");
                }
        ));

        assertArrayEquals(before, Files.readAllBytes(path));
        try (var paths = Files.list(temporaryDirectory)) {
            assertEquals(List.of(path), paths.toList());
        }
    }

    @Test
    void staleExpectedChecksumPreventsMutation() throws Exception {
        Path path = temporaryDirectory.resolve(ReviewDecisionSource.FILE_NAME);
        ReviewDecisionSource current = ReviewDecisionSource.empty();
        Files.write(path, current.canonicalBytes());

        assertReason(ReviewFailureReason.CONFLICTING_DECISION, () -> ReviewDecisionSource.writeAtomically(
                path,
                ReviewTestSupport.sha(99),
                ReviewTestSupport.verifiedSource(ReviewTestSupport.fixture(), false)
        ));
        assertArrayEquals(current.canonicalBytes(), Files.readAllBytes(path));
    }

    private static void assertReason(ReviewFailureReason reason, ThrowingOperation operation) {
        ReviewValidationException exception = assertThrows(ReviewValidationException.class, operation::run);
        assertEquals(reason, exception.reason());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
