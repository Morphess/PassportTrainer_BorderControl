package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.model.QuizAnswer;
import ua.passporttrainer.model.QuizContent;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSession;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuizDomainInvariantTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    @Test
    void rejectsCorrectAnswerOutsideOptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuizQuestion(
                        1,
                        QuizQuestionType.PASSPORT_TO_COUNTRY,
                        document(101, "entity:france"),
                        countryOptions(),
                        "entity:france"
                )
        );
    }

    @Test
    void rejectsDuplicateQuestionOptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuizQuestion(
                        1,
                        QuizQuestionType.PASSPORT_TO_COUNTRY,
                        document(101, "entity:ukraine"),
                        List.of(
                                new QuizContent.Country("entity:ukraine", "Ukraine"),
                                new QuizContent.Country("entity:ukraine", "Україна")
                        ),
                        "entity:ukraine"
                )
        );
    }

    @Test
    void rejectsInvalidQuestionDefinition() {
        QuizContent.Document subject = document(101, "entity:ukraine");
        List<QuizContent> options = countryOptions();

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new QuizQuestion(1, null, subject, options, "entity:ukraine")
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new QuizQuestion(
                                1, QuizQuestionType.PASSPORT_TO_COUNTRY,
                                null, options, "entity:ukraine"
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new QuizQuestion(
                                1, QuizQuestionType.PASSPORT_TO_COUNTRY,
                                subject, null, "entity:ukraine"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new QuizQuestion(
                                1, QuizQuestionType.PASSPORT_TO_COUNTRY,
                                subject, List.of(), "entity:ukraine"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new QuizQuestion(
                                1, QuizQuestionType.PASSPORT_TO_COUNTRY,
                                subject, List.of(options.getFirst()), "entity:ukraine"
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new QuizQuestion(
                                1, QuizQuestionType.PASSPORT_TO_COUNTRY,
                                subject, options, null
                        )
                )
        );
    }

    @Test
    void rejectsInvalidLifecycleInputs() {
        QuizSessionLifecycleService service = new QuizSessionLifecycleService(
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        QuizQuestion question = question(1, "entity:ukraine");

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> service.startSession(1, 2, null, List.of(question))
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> service.startSession(1, 2, QuizMode.TRAINING, null)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> service.submitAnswer(null, 1, "entity:ukraine")
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> service.submitAnswer(
                                service.startSession(1, 2, QuizMode.TRAINING, List.of(question)),
                                1,
                                null
                        )
                )
        );
    }

    @Test
    void rejectsInvalidAnswerFields() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new QuizAnswer(1, null, false, NOW)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new QuizAnswer(1, "entity:ukraine", true, null)
                )
        );
    }

    @Test
    void rejectsInconsistentPrebuiltSessionAnswers() {
        QuizQuestion question = question(1, "entity:ukraine");
        QuizAnswer unknownQuestion = new QuizAnswer(2, "entity:ukraine", true, NOW);
        QuizAnswer wrongCorrectness = new QuizAnswer(1, "entity:ukraine", false, NOW);
        QuizAnswer valid = new QuizAnswer(1, "entity:ukraine", true, NOW);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new QuizSession(
                                1, 2, QuizMode.TRAINING, NOW, null,
                                List.of(question), List.of(unknownQuestion)
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new QuizSession(
                                1, 2, QuizMode.TRAINING, NOW, null,
                                List.of(question), List.of(wrongCorrectness)
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new QuizSession(
                                1, 2, QuizMode.TRAINING, NOW, null,
                                List.of(question), List.of(valid, valid)
                        )
                )
        );
    }

    @Test
    void rejectsInvalidExamResultValues() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ExamResult(1, 2, 3, -1, 1, 0, NOW)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ExamResult(1, 2, 3, 2, 1, 0, NOW)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ExamResult(1, 2, 3, 0, 0, 0, NOW)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ExamResult(1, 2, 3, 0, 1, -1, NOW)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ExamResult(1, 2, 3, 0, 1, 0, null)
                )
        );
    }

    private static QuizQuestion question(long id, String correctAnswerId) {
        return new QuizQuestion(
                id,
                QuizQuestionType.PASSPORT_TO_COUNTRY,
                document(100 + id, correctAnswerId),
                countryOptions(),
                correctAnswerId
        );
    }

    private static List<QuizContent> countryOptions() {
        return List.of(
                new QuizContent.Country("entity:ukraine", "Ukraine"),
                new QuizContent.Country("entity:poland", "Poland")
        );
    }

    private static QuizContent.Document document(long id, String entityId) {
        return new QuizContent.Document(
                "document:" + id,
                entityId,
                DocumentType.ORDINARY_PASSPORT,
                "Ordinary passport",
                "image:" + id,
                "fixtures/" + id + ".jpg"
        );
    }
}
