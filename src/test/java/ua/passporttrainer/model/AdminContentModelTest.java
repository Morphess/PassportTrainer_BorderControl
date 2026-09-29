package ua.passporttrainer.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminContentModelTest {

    private static final Instant CREATED = Instant.parse("2026-09-29T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-09-29T10:05:00Z");

    @Test
    void questionValidatesStableIdentityCreatorAndTimestamps() {
        Question question = new Question(
                "question:1", QuestionLifecycleState.DRAFT, 7, CREATED, UPDATED
        );

        assertEquals("question:1", question.questionId());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Question(" ", QuestionLifecycleState.DRAFT, 7, CREATED, UPDATED)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Question("question:1", QuestionLifecycleState.DRAFT, 0, CREATED, UPDATED)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Question("question:1", QuestionLifecycleState.DRAFT, 7, UPDATED, CREATED)
        );
    }

    @Test
    void questionVersionDefensivelyPreservesImmutableOrderedOptions() {
        List<QuestionOption> source = new ArrayList<>(validOptions());
        QuestionVersion version = version(source);
        source.clear();

        assertEquals(2, version.options().size());
        assertEquals("option:correct", version.options().getFirst().optionId());
        assertThrows(
                UnsupportedOperationException.class,
                () -> version.options().add(new QuestionOption("option:3", 2, "Third", false))
        );
    }

    @Test
    void questionVersionRejectsAmbiguousOrInvalidContent() {
        assertThrows(IllegalArgumentException.class, () -> version(List.of(
                new QuestionOption("option:1", 0, "Only", true)
        )));
        assertThrows(IllegalArgumentException.class, () -> version(List.of(
                new QuestionOption("option:1", 0, "First", true),
                new QuestionOption("option:2", 1, "Second", true)
        )));
        assertThrows(IllegalArgumentException.class, () -> version(List.of(
                new QuestionOption("option:1", 0, "First", true),
                new QuestionOption("option:1", 1, "Second", false)
        )));
        assertThrows(IllegalArgumentException.class, () -> version(List.of(
                new QuestionOption("option:1", 1, "First", true),
                new QuestionOption("option:2", 2, "Second", false)
        )));
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuestionVersion(
                        "version:1",
                        "question:1",
                        1,
                        AuthoredQuestionType.THEORETICAL,
                        "Prompt",
                        Optional.of(" "),
                        Optional.empty(),
                        Optional.empty(),
                        validOptions(),
                        7,
                        CREATED
                )
        );
    }

    @Test
    void mediaMetadataValidatesOptionalSizeHashAndArchiveState() {
        MediaMetadata media = new MediaMetadata(
                "media:1",
                MediaReferenceType.UPLOADED_FILE,
                "question-media/media-1.png",
                "image/png",
                OptionalLong.of(128),
                Optional.of("a".repeat(64)),
                7,
                CREATED,
                UPDATED,
                false
        );

        assertFalse(media.archived());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MediaMetadata(
                        "media:1",
                        MediaReferenceType.UPLOADED_FILE,
                        "question-media/media-1.png",
                        "image/png",
                        OptionalLong.of(-1),
                        Optional.empty(),
                        7,
                        CREATED,
                        UPDATED,
                        false
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MediaMetadata(
                        "media:1",
                        MediaReferenceType.UPLOADED_FILE,
                        "question-media/media-1.png",
                        "image/png",
                        OptionalLong.empty(),
                        Optional.of("A".repeat(64)),
                        7,
                        CREATED,
                        UPDATED,
                        false
                )
        );
    }

    @Test
    void assessmentDefinitionValidatesMinimalLifecycleMetadata() {
        AssessmentDefinition assessment = new AssessmentDefinition(
                "assessment:1",
                AssessmentState.DRAFT,
                "Border-control basics",
                Optional.empty(),
                7,
                CREATED,
                UPDATED
        );

        assertEquals(AssessmentState.DRAFT, assessment.state());
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssessmentDefinition(
                        "assessment:1",
                        AssessmentState.DRAFT,
                        "Border-control basics",
                        Optional.of(" "),
                        7,
                        CREATED,
                        UPDATED
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssessmentDefinition(
                        "assessment:1",
                        AssessmentState.DRAFT,
                        "Border-control basics",
                        Optional.empty(),
                        7,
                        UPDATED,
                        CREATED
                )
        );
    }

    private static QuestionVersion version(List<QuestionOption> options) {
        return new QuestionVersion(
                "version:1",
                "question:1",
                1,
                AuthoredQuestionType.THEORETICAL,
                "Which document is required?",
                Optional.empty(),
                Optional.of("Explanation"),
                Optional.of("BASIC"),
                options,
                7,
                CREATED
        );
    }

    private static List<QuestionOption> validOptions() {
        return List.of(
                new QuestionOption("option:correct", 0, "Passport", true),
                new QuestionOption("option:wrong", 1, "Receipt", false)
        );
    }
}
