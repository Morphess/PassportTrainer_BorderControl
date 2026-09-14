package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.model.QuizAnswer;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
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
        QuizSession session = startSession(QuizMode.TRAINING, question(1, "Ukraine"));

        QuizSession answered = quizService.submitAnswer(session, 1, "Ukraine");

        QuizAnswer answer = answered.answers().getFirst();
        assertAll(
                () -> assertEquals(1, answered.answers().size()),
                () -> assertEquals("Ukraine", answer.selectedAnswer()),
                () -> assertTrue(answer.correct()),
                () -> assertEquals(NOW, answer.answeredAt())
        );
    }

    @Test
    void recordsIncorrectAnswer() {
        QuizSession session = startSession(QuizMode.TRAINING, question(1, "Ukraine"));

        QuizSession answered = quizService.submitAnswer(session, 1, "Poland");

        assertFalse(answered.answers().getFirst().correct());
    }

    @Test
    void rejectsDuplicateAnswerWithoutIncreasingScore() {
        QuizSession session = startSession(QuizMode.EXAMINATION, question(1, "Ukraine"));
        QuizSession answered = quizService.submitAnswer(session, 1, "Ukraine");

        assertThrows(
                IllegalStateException.class,
                () -> quizService.submitAnswer(answered, 1, "Ukraine")
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
                startSession(QuizMode.TRAINING, question(1, "Ukraine"))
        );

        assertThrows(
                IllegalStateException.class,
                () -> quizService.submitAnswer(completed, 1, "Ukraine")
        );
    }

    @Test
    void rejectsSecondSessionCompletion() {
        QuizSession completed = quizService.completeSession(
                startSession(QuizMode.TRAINING, question(1, "Ukraine"))
        );

        assertThrows(IllegalStateException.class, () -> quizService.completeSession(completed));
    }

    @Test
    void rejectsDuplicateQuestionIds() {
        QuizQuestion first = question(1, "Ukraine");
        QuizQuestion duplicate = new QuizQuestion(
                1,
                102,
                "Identify the second document",
                List.of("France", "Spain"),
                "France"
        );

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
                        101,
                        "Identify the country",
                        List.of("Ukraine", "Poland"),
                        "France"
                )
        );
    }

    @Test
    void rejectsDuplicateQuestionOptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuizQuestion(
                        1,
                        101,
                        "Identify the country",
                        List.of("Ukraine", "Ukraine"),
                        "Ukraine"
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
                startSession(QuizMode.TRAINING, question(1, "Ukraine"))
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
                List.of(question(1, "Ukraine"), question(2, "Poland"))
        );
        QuizSession firstAnswered = quizService.submitAnswer(session, 1, "Ukraine");
        QuizSession allAnswered = quizService.submitAnswer(firstAnswered, 2, "Ukraine");
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
        QuizSession examination = startSession(QuizMode.EXAMINATION, question(1, "Ukraine"));

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
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new QuizQuestion(1, 101, null, List.of("Ukraine"), "Ukraine")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new QuizQuestion(1, 101, " ", List.of("Ukraine"), "Ukraine")
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new QuizQuestion(1, 101, "Country", null, "Ukraine")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new QuizQuestion(1, 101, "Country", List.of(), "Ukraine")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new QuizQuestion(
                                1,
                                101,
                                "Country",
                                List.of("Ukraine"),
                                "Ukraine"
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new QuizQuestion(1, 101, "Country", List.of("Ukraine"), null)
                )
        );
    }

    @Test
    void rejectsInvalidSessionInputs() {
        QuizQuestion question = question(1, "Ukraine");

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
                        () -> quizService.submitAnswer(null, 1, "Ukraine")
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> quizService.submitAnswer(
                                startSession(QuizMode.TRAINING, question),
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
                        () -> new QuizAnswer(1, "Ukraine", true, null)
                )
        );
    }

    @Test
    void rejectsInconsistentPrebuiltSessionAnswers() {
        QuizQuestion question = question(1, "Ukraine");
        QuizAnswer unknownQuestion = new QuizAnswer(2, "Ukraine", true, NOW);
        QuizAnswer wrongCorrectness = new QuizAnswer(1, "Ukraine", false, NOW);
        QuizAnswer valid = new QuizAnswer(1, "Ukraine", true, NOW);

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

    private static QuizQuestion question(long id, String correctAnswer) {
        return new QuizQuestion(
                id,
                100 + id,
                "Identify the country",
                List.of("Ukraine", "Poland"),
                correctAnswer
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
