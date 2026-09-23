package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizSessionLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private QuizSessionLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new QuizSessionLifecycleService(CLOCK);
    }

    @Test
    void recordsSuccessfulAnswer() {
        QuizSession session = start(question(1, "entity:ukraine"));

        QuizSession answered = lifecycleService.submitAnswer(session, 1, "entity:ukraine");

        QuizAnswer answer = answered.answers().getFirst();
        assertAll(
                () -> assertEquals("entity:ukraine", answer.selectedAnswerId()),
                () -> assertTrue(answer.correct()),
                () -> assertEquals(NOW, answer.answeredAt())
        );
    }

    @Test
    void recordsIncorrectAnswer() {
        QuizSession answered = lifecycleService.submitAnswer(
                start(question(1, "entity:ukraine")), 1, "entity:poland"
        );

        assertFalse(answered.answers().getFirst().correct());
    }

    @Test
    void rejectsDuplicateAnswer() {
        QuizSession answered = lifecycleService.submitAnswer(
                start(question(1, "entity:ukraine")), 1, "entity:ukraine"
        );

        assertThrows(
                IllegalStateException.class,
                () -> lifecycleService.submitAnswer(answered, 1, "entity:ukraine")
        );
        assertEquals(1, answered.answers().size());
    }

    @Test
    void rejectsEarlyCompletion() {
        QuizSession session = start(
                question(1, "entity:ukraine"),
                question(2, "entity:poland")
        );
        QuizSession partlyAnswered = lifecycleService.submitAnswer(
                session, 1, "entity:ukraine"
        );

        assertThrows(
                IllegalStateException.class,
                () -> lifecycleService.completeSession(partlyAnswered)
        );
    }

    @Test
    void completesAfterEveryQuestionHasOneAnswer() {
        QuizSession session = start(
                question(1, "entity:ukraine"),
                question(2, "entity:poland")
        );
        session = lifecycleService.submitAnswer(session, 1, "entity:ukraine");
        session = lifecycleService.submitAnswer(session, 2, "entity:ukraine");

        QuizSession completed = lifecycleService.completeSession(session);

        assertAll(
                () -> assertTrue(completed.completed()),
                () -> assertEquals(NOW, completed.completedAt()),
                () -> assertEquals(2, completed.answers().size())
        );
    }

    @Test
    void rejectsRepeatedCompletion() {
        QuizSession answered = lifecycleService.submitAnswer(
                start(question(1, "entity:ukraine")), 1, "entity:ukraine"
        );
        QuizSession completed = lifecycleService.completeSession(answered);

        assertThrows(
                IllegalStateException.class,
                () -> lifecycleService.completeSession(completed)
        );
    }

    @Test
    void rejectsAnswerAfterCompletion() {
        QuizSession answered = lifecycleService.submitAnswer(
                start(question(1, "entity:ukraine")), 1, "entity:ukraine"
        );
        QuizSession completed = lifecycleService.completeSession(answered);

        assertThrows(
                IllegalStateException.class,
                () -> lifecycleService.submitAnswer(completed, 1, "entity:ukraine")
        );
    }

    @Test
    void rejectsUnknownQuestionAndAnswerOption() {
        QuizSession session = start(question(1, "entity:ukraine"));

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> lifecycleService.submitAnswer(session, 2, "entity:ukraine")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> lifecycleService.submitAnswer(session, 1, "entity:france")
                )
        );
    }

    @Test
    void rejectsEmptySession() {
        assertThrows(
                IllegalArgumentException.class,
                () -> lifecycleService.startSession(1, 2, QuizMode.TRAINING, List.of())
        );
    }

    @Test
    void rejectsDuplicateQuestionIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> lifecycleService.startSession(
                        1,
                        2,
                        QuizMode.TRAINING,
                        List.of(question(1, "entity:ukraine"), question(1, "entity:poland"))
                )
        );
    }

    private QuizSession start(QuizQuestion... questions) {
        return lifecycleService.startSession(1, 2, QuizMode.TRAINING, List.of(questions));
    }

    static QuizQuestion question(long id, String correctAnswerId) {
        return new QuizQuestion(
                id,
                QuizQuestionType.PASSPORT_TO_COUNTRY,
                new QuizContent.Document(
                        "document:" + id,
                        correctAnswerId,
                        DocumentType.ORDINARY_PASSPORT,
                        "Ordinary passport",
                        "image:" + id,
                        "fixtures/" + id + ".jpg"
                ),
                List.of(
                        new QuizContent.Country("entity:ukraine", "Ukraine"),
                        new QuizContent.Country("entity:poland", "Poland")
                ),
                correctAnswerId
        );
    }
}
