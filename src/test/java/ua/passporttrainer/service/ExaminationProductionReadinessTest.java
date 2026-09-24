package ua.passporttrainer.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.model.QuizGenerationRequest;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSession;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExaminationProductionReadinessTest {

    private static final Path PRODUCTION_CATALOGUE =
            Path.of("data", "passport-catalogue", "passport-catalogue.sqlite");
    private static final long FIXED_SEED = 20260924L;

    @Test
    void probesDeterministicTenQuestionExaminationCapacity() throws Exception {
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(PRODUCTION_CATALOGUE);
        QuizGenerationRequest request = new QuizGenerationRequest(
                800L,
                QuizSessionCoordinator.ANONYMOUS_USER_ID,
                QuizMode.EXAMINATION,
                10,
                4,
                Set.of(QuizQuestionType.values()),
                Set.of(),
                Set.of(),
                Set.of(),
                FIXED_SEED
        );

        QuizSession first = context.quizGenerationService().generate(request);
        QuizSession second = context.quizGenerationService().generate(request);

        QuizSession answered = first;
        QuizSessionLifecycleService lifecycleService = new QuizSessionLifecycleService(Clock.fixed(
                first.startedAt().plusSeconds(600),
                ZoneOffset.UTC
        ));
        for (QuizQuestion question : first.questions()) {
            answered = lifecycleService.submitAnswer(
                    answered,
                    question.id(),
                    question.correctAnswerId()
            );
        }
        QuizSession completed = lifecycleService.completeSession(answered);

        assertAll(
                () -> assertEquals(QuizMode.EXAMINATION, first.mode()),
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
                () -> assertEquals(first.startedAt().plusSeconds(600), completed.completedAt())
        );
    }
}
