package ua.passporttrainer.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.database.ApplicationDatabaseContext;
import ua.passporttrainer.model.AssessmentSource;
import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.model.QuizSessionConfiguration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExaminationProductionReadinessTest {

    private static final Path PRODUCTION_CATALOGUE =
            Path.of("data", "passport-catalogue", "passport-catalogue.sqlite");
    private static final long FIXED_SEED = 20260924L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void runsDeterministicFixedExaminationAgainstReadOnlyProductionCatalogue() throws Exception {
        byte[] catalogueBefore = Files.readAllBytes(PRODUCTION_CATALOGUE);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(PRODUCTION_CATALOGUE);
        QuizSessionConfiguration configuration = new ExaminationConfigurationFactory().create();
        QuizSessionCoordinator firstCoordinator = coordinator(context, 800L);
        QuizSessionCoordinator secondCoordinator = coordinator(context, 801L);
        firstCoordinator.configure(configuration);
        secondCoordinator.configure(configuration);

        QuizSession first = firstCoordinator.start();
        QuizSession second = secondCoordinator.start();

        for (int index = 0; index < first.questions().size(); index++) {
            QuizQuestion question = firstCoordinator.currentQuestion();
            assertInstanceOf(
                    QuizAnswerSubmissionResult.Accepted.class,
                    firstCoordinator.submitAnswer(question.correctAnswerId())
            );
            assertTrue(firstCoordinator.visibleScore().isEmpty());
            if (index + 1 < first.questions().size()) {
                firstCoordinator.advance();
            }
        }
        QuizSession completed = firstCoordinator.complete();
        ApplicationDatabaseContext applicationDatabase = ApplicationDatabaseContext.open(
                temporaryDirectory.resolve("application.sqlite")
        );
        ExamResult saved = applicationDatabase.examResultService().saveExamResult(
                completed.id(),
                completed,
                AssessmentSource.GENERATED,
                firstCoordinator.assessmentProvenance()
        );
        ExamResult reloaded = applicationDatabase.resultRepository()
                .findById(saved.id())
                .orElseThrow();

        assertAll(
                () -> assertEquals(QuizMode.EXAMINATION, configuration.mode()),
                () -> assertEquals(10, configuration.questionCount()),
                () -> assertEquals(4, configuration.optionCount()),
                () -> assertEquals(
                        Set.of(QuizQuestionType.values()),
                        configuration.allowedQuestionTypes()
                ),
                () -> assertTrue(configuration.continentIds().isEmpty()),
                () -> assertTrue(configuration.entityIds().isEmpty()),
                () -> assertTrue(configuration.documentTypes().isEmpty()),
                () -> assertEquals(10, first.questions().size()),
                () -> assertTrue(first.answers().isEmpty()),
                () -> assertFalse(first.completed()),
                () -> assertEquals(first.questions(), second.questions()),
                () -> assertEquals(
                        10,
                        first.questions().stream().map(QuizQuestion::id).distinct().count()
                ),
                () -> assertTrue(first.questions().stream().allMatch(question ->
                        question.options().size() == 4
                                && question.hasOption(question.correctAnswerId())
                )),
                () -> assertTrue(completed.completed()),
                () -> assertEquals(10, completed.answers().size()),
                () -> assertEquals(10, firstCoordinator.visibleScore().orElseThrow()),
                () -> assertEquals(saved, reloaded),
                () -> assertEquals(AssessmentSource.GENERATED, reloaded.assessmentSource()),
                () -> assertTrue(reloaded.assessmentProvenance().isEmpty()),
                () -> assertEquals(CLOCK.instant(), reloaded.startedAt()),
                () -> assertEquals(CLOCK.instant(), reloaded.completedAt()),
                () -> assertEquals(10, reloaded.correctAnswers()),
                () -> assertEquals(10, reloaded.totalQuestions()),
                () -> assertEquals(0, reloaded.durationSeconds()),
                () -> assertNull(reloaded.userId()),
                () -> assertArrayEquals(
                        catalogueBefore,
                        Files.readAllBytes(PRODUCTION_CATALOGUE)
                )
        );
    }

    private static QuizSessionCoordinator coordinator(
            CatalogueApplicationContext context,
            long sessionId
    ) {
        return new QuizSessionCoordinator(
                new GeneratedQuestionSetProvider(context.quizGenerationService()),
                new QuizSessionLifecycleService(CLOCK),
                () -> sessionId,
                () -> FIXED_SEED
        );
    }
}
