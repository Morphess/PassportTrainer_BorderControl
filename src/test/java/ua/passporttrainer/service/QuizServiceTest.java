package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.QuizContent;
import ua.passporttrainer.model.QuizAnswer;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.repository.ResultRepository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private CapturingResultRepository resultRepository;
    private QuizService quizService;

    @BeforeEach
    void setUp() {
        resultRepository = new CapturingResultRepository();
        quizService = new QuizService(resultRepository, CLOCK);
    }

    @Test
    void recordsCorrectAnswer() {
        QuizSession session = startSession(QuizMode.TRAINING, question(1, "entity:ukraine"));

        QuizSession answered = quizService.submitAnswer(session, 1, "entity:ukraine");

        QuizAnswer answer = answered.answers().getFirst();
        assertAll(
                () -> assertEquals(1, answered.answers().size()),
                () -> assertEquals("entity:ukraine", answer.selectedAnswerId()),
                () -> assertTrue(answer.correct()),
                () -> assertEquals(NOW, answer.answeredAt())
        );
    }

    @Test
    void recordsIncorrectAnswer() {
        QuizSession session = startSession(QuizMode.TRAINING, question(1, "entity:ukraine"));

        QuizSession answered = quizService.submitAnswer(session, 1, "entity:poland");

        assertFalse(answered.answers().getFirst().correct());
    }

    @Test
    void rejectsDuplicateAnswerWithoutIncreasingScore() {
        QuizSession session = startSession(QuizMode.EXAMINATION, question(1, "entity:ukraine"));
        QuizSession answered = quizService.submitAnswer(session, 1, "entity:ukraine");

        assertThrows(
                IllegalStateException.class,
                () -> quizService.submitAnswer(answered, 1, "entity:ukraine")
        );

        ExamResult result = quizService.saveExamResult(10, quizService.completeSession(answered));
        assertAll(
                () -> assertEquals(1, answered.answers().size()),
                () -> assertEquals(1, result.correctAnswers()),
                () -> assertEquals(1, result.totalQuestions()),
                () -> assertEquals(100.0, result.scorePercent())
        );
    }

    @Test
    void rejectsAnswerAfterSessionCompletion() {
        QuizSession completed = quizService.completeSession(
                startSession(QuizMode.TRAINING, question(1, "entity:ukraine"))
        );

        assertThrows(
                IllegalStateException.class,
                () -> quizService.submitAnswer(completed, 1, "entity:ukraine")
        );
    }

    @Test
    void rejectsSecondSessionCompletion() {
        QuizSession completed = quizService.completeSession(
                startSession(QuizMode.TRAINING, question(1, "entity:ukraine"))
        );

        assertThrows(IllegalStateException.class, () -> quizService.completeSession(completed));
    }

    @Test
    void rejectsDuplicateQuestionIds() {
        QuizQuestion first = question(1, "entity:ukraine");
        QuizQuestion duplicate = question(1, "entity:poland");

        assertThrows(
                IllegalArgumentException.class,
                () -> quizService.startSession(1, 2, QuizMode.TRAINING, List.of(first, duplicate))
        );
    }

    @Test
    void rejectsCorrectAnswerOutsideOptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuizQuestion(
                        1,
                        QuizQuestionType.PASSPORT_TO_COUNTRY,
                        document(101, "entity:france"),
                        List.of(
                                new QuizContent.Country("entity:ukraine", "Ukraine"),
                                new QuizContent.Country("entity:poland", "Poland")
                        ),
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
    void rejectsExamResultWhoseScoreExceedsQuestionCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExamResult(1, 2, 3, 2, 1, 10, NOW)
        );
    }

    @Test
    void savesOnlyExaminationResults() {
        QuizSession completedTraining = quizService.completeSession(
                startSession(QuizMode.TRAINING, question(1, "entity:ukraine"))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> quizService.saveExamResult(10, completedTraining)
        );
        assertNull(resultRepository.savedResult);
    }

    @Test
    void savesCompletedExaminationResultAndCalculatesPercentage() {
        QuizSession session = quizService.startSession(
                1,
                2,
                QuizMode.EXAMINATION,
                List.of(question(1, "entity:ukraine"), question(2, "entity:poland"))
        );
        QuizSession firstAnswered = quizService.submitAnswer(session, 1, "entity:ukraine");
        QuizSession allAnswered = quizService.submitAnswer(firstAnswered, 2, "entity:ukraine");
        QuizSession completed = quizService.completeSession(allAnswered);

        ExamResult result = quizService.saveExamResult(10, completed);

        assertAll(
                () -> assertEquals(1, result.correctAnswers()),
                () -> assertEquals(2, result.totalQuestions()),
                () -> assertEquals(50.0, result.scorePercent()),
                () -> assertEquals(0, result.durationSeconds()),
                () -> assertEquals(NOW, result.completedAt()),
                () -> assertSame(result, resultRepository.savedResult)
        );
    }

    @Test
    void rejectsSavingIncompleteExamination() {
        QuizSession examination = startSession(QuizMode.EXAMINATION, question(1, "entity:ukraine"));

        assertThrows(
                IllegalStateException.class,
                () -> quizService.saveExamResult(10, examination)
        );
        assertNull(resultRepository.savedResult);
    }

    @Test
    void rejectsEmptySession() {
        assertThrows(
                IllegalArgumentException.class,
                () -> quizService.startSession(1, 2, QuizMode.TRAINING, List.of())
        );
    }

    @Test
    void rejectsInvalidQuestionDefinition() {
        QuizContent.Document subject = document(101, "entity:ukraine");
        List<QuizContent> options = List.of(
                new QuizContent.Country("entity:ukraine", "Ukraine"),
                new QuizContent.Country("entity:poland", "Poland")
        );

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
    void rejectsInvalidSessionInputs() {
        QuizQuestion question = question(1, "entity:ukraine");

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> quizService.startSession(1, 2, null, List.of(question))
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> quizService.startSession(1, 2, QuizMode.TRAINING, null)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> quizService.submitAnswer(null, 1, "entity:ukraine")
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> quizService.submitAnswer(
                                startSession(QuizMode.TRAINING, question),
                                1,
                                null
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> quizService.submitAnswer(
                                startSession(QuizMode.TRAINING, question),
                                1,
                                "entity:france"
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

    private QuizSession startSession(QuizMode mode, QuizQuestion question) {
        return quizService.startSession(1, 2, mode, List.of(question));
    }

    private static QuizQuestion question(long id, String correctAnswerId) {
        return new QuizQuestion(
                id,
                QuizQuestionType.PASSPORT_TO_COUNTRY,
                document(100 + id, correctAnswerId),
                List.of(
                        new QuizContent.Country("entity:ukraine", "Ukraine"),
                        new QuizContent.Country("entity:poland", "Poland")
                ),
                correctAnswerId
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

    private static final class CapturingResultRepository implements ResultRepository {

        private ExamResult savedResult;

        @Override
        public ExamResult save(ExamResult result) {
            savedResult = result;
            return result;
        }

        @Override
        public Optional<ExamResult> findById(long id) {
            return Optional.empty();
        }

        @Override
        public List<ExamResult> findByUserId(long userId) {
            return List.of();
        }
    }
}
