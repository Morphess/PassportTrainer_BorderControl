package ua.passporttrainer.presentation;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.QuizAnswer;
import ua.passporttrainer.model.QuizContent;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSession;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExaminationResultPresentationTest {

    @Test
    void derivesCountsPercentageAndDurationFromCompletedSession() {
        Instant startedAt = Instant.parse("2026-09-24T10:00:00Z");
        QuizQuestion first = question(1);
        QuizQuestion second = question(2);
        QuizSession completed = new QuizSession(
                9L,
                0L,
                QuizMode.EXAMINATION,
                startedAt,
                startedAt.plusSeconds(125),
                List.of(first, second),
                List.of(
                        new QuizAnswer(1L, first.correctAnswerId(), true, startedAt.plusSeconds(30)),
                        new QuizAnswer(2L, "DIPLOMATIC_PASSPORT", false, startedAt.plusSeconds(120))
                )
        );

        ExaminationResultPresentation result =
                ExaminationResultPresentation.fromCompletedSession(completed, 1);

        assertAll(
                () -> assertEquals(1, result.correctCount()),
                () -> assertEquals(1, result.incorrectCount()),
                () -> assertEquals(2, result.totalCount()),
                () -> assertEquals(50, result.percentage()),
                () -> assertEquals(125, result.durationSeconds())
        );
    }

    @Test
    void rejectsResultBeforeCompletion() {
        Instant startedAt = Instant.parse("2026-09-24T10:00:00Z");
        QuizSession active = new QuizSession(
                9L,
                0L,
                QuizMode.EXAMINATION,
                startedAt,
                null,
                List.of(question(1)),
                List.of()
        );

        assertThrows(
                IllegalStateException.class,
                () -> ExaminationResultPresentation.fromCompletedSession(active, 0)
        );
    }

    private static QuizQuestion question(long id) {
        return new QuizQuestion(
                id,
                QuizQuestionType.DOCUMENT_TYPE,
                new QuizContent.Document(
                        "document:" + id,
                        "entity:" + id,
                        DocumentType.ORDINARY_PASSPORT,
                        "Document " + id,
                        "image:" + id,
                        "fixtures/" + id + ".jpg"
                ),
                List.of(
                        new QuizContent.DocumentTypeValue(DocumentType.ORDINARY_PASSPORT),
                        new QuizContent.DocumentTypeValue(DocumentType.DIPLOMATIC_PASSPORT)
                ),
                DocumentType.ORDINARY_PASSPORT.name()
        );
    }
}
