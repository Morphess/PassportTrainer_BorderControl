package ua.passporttrainer.service;

import java.time.Instant;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamResultServiceTest {

    private static final Instant STARTED = Instant.parse("2026-09-21T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-09-21T10:02:00Z");

    private CapturingResultRepository repository;
    private ExamResultService resultService;

    @BeforeEach
    void setUp() {
        repository = new CapturingResultRepository();
        resultService = new ExamResultService(repository);
    }

    @Test
    void savesOnlyExaminationResults() {
        QuizSession training = completedSession(QuizMode.TRAINING, true);

        assertThrows(
                IllegalArgumentException.class,
                () -> resultService.saveExamResult(10, training)
        );
        assertNull(repository.savedResult);
    }

    @Test
    void savesFullyAnsweredCompletedExamination() {
        QuizSession examination = completedSession(QuizMode.EXAMINATION, true);

        ExamResult result = resultService.saveExamResult(10, examination);

        assertAll(
                () -> assertEquals(1, result.correctAnswers()),
                () -> assertEquals(1, result.totalQuestions()),
                () -> assertEquals(100.0, result.scorePercent()),
                () -> assertEquals(120, result.durationSeconds()),
                () -> assertSame(result, repository.savedResult)
        );
    }

    @Test
    void rejectsIncompleteExamination() {
        QuizQuestion question = QuizSessionLifecycleServiceTest.question(1, "entity:ukraine");
        QuizSession examination = new QuizSession(
                1, 2, QuizMode.EXAMINATION, STARTED, null, List.of(question), List.of()
        );

        assertThrows(
                IllegalStateException.class,
                () -> resultService.saveExamResult(10, examination)
        );
        assertNull(repository.savedResult);
    }

    @Test
    void rejectsCompletedExaminationWithUnansweredQuestion() {
        QuizQuestion question = QuizSessionLifecycleServiceTest.question(1, "entity:ukraine");
        QuizSession examination = new QuizSession(
                1, 2, QuizMode.EXAMINATION, STARTED, COMPLETED, List.of(question), List.of()
        );

        assertThrows(
                IllegalStateException.class,
                () -> resultService.saveExamResult(10, examination)
        );
        assertNull(repository.savedResult);
    }

    private static QuizSession completedSession(QuizMode mode, boolean correct) {
        QuizQuestion question = QuizSessionLifecycleServiceTest.question(1, "entity:ukraine");
        QuizAnswer answer = new QuizAnswer(
                1,
                correct ? "entity:ukraine" : "entity:poland",
                correct,
                STARTED
        );
        return new QuizSession(
                1, 2, mode, STARTED, COMPLETED, List.of(question), List.of(answer)
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
