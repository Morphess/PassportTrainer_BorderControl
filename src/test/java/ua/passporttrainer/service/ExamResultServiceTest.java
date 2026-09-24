package ua.passporttrainer.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.AssessmentProvenance;
import ua.passporttrainer.model.AssessmentSource;
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
        QuizSession training = completedSession(QuizMode.TRAINING, true, 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> resultService.saveExamResult(
                        10, training, AssessmentSource.GENERATED, Optional.empty()
                )
        );
        assertNull(repository.savedResult);
    }

    @Test
    void savesFullyAnsweredCompletedExamination() {
        QuizSession examination = completedSession(QuizMode.EXAMINATION, true, 2);
        AssessmentProvenance provenance = new AssessmentProvenance(
                "assessment:future", "version:1"
        );

        ExamResult result = resultService.saveExamResult(
                10, examination, AssessmentSource.AUTHORED, Optional.of(provenance)
        );

        assertAll(
                () -> assertEquals(1, result.correctAnswers()),
                () -> assertEquals(1, result.totalQuestions()),
                () -> assertEquals(100.0, result.scorePercent()),
                () -> assertEquals(120, result.durationSeconds()),
                () -> assertEquals(STARTED, result.startedAt()),
                () -> assertEquals(COMPLETED, result.completedAt()),
                () -> assertEquals(AssessmentSource.AUTHORED, result.assessmentSource()),
                () -> assertEquals(Optional.of(provenance), result.assessmentProvenance()),
                () -> assertSame(result, repository.savedResult)
        );
    }

    @Test
    void mapsTemporaryAnonymousIdentityToNullForGeneratedResult() {
        QuizSession examination = completedSession(
                QuizMode.EXAMINATION, true, QuizSessionCoordinator.ANONYMOUS_USER_ID
        );

        ExamResult result = resultService.saveExamResult(
                10, examination, AssessmentSource.GENERATED, Optional.empty()
        );

        assertAll(
                () -> assertNull(result.userId()),
                () -> assertEquals(AssessmentSource.GENERATED, result.assessmentSource()),
                () -> assertEquals(Optional.empty(), result.assessmentProvenance())
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
                () -> resultService.saveExamResult(
                        10, examination, AssessmentSource.GENERATED, Optional.empty()
                )
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
                () -> resultService.saveExamResult(
                        10, examination, AssessmentSource.GENERATED, Optional.empty()
                )
        );
        assertNull(repository.savedResult);
    }

    private static QuizSession completedSession(
            QuizMode mode,
            boolean correct,
            long userId
    ) {
        QuizQuestion question = QuizSessionLifecycleServiceTest.question(1, "entity:ukraine");
        QuizAnswer answer = new QuizAnswer(
                1,
                correct ? "entity:ukraine" : "entity:poland",
                correct,
                STARTED
        );
        return new QuizSession(
                1, userId, mode, STARTED, COMPLETED, List.of(question), List.of(answer)
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
