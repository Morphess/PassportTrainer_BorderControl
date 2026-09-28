package ua.passporttrainer.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.service.GeneratedQuestionSetProvider;
import ua.passporttrainer.image.FileSystemPassportImageLocator;
import ua.passporttrainer.model.QuizContent;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.model.QuizSessionConfiguration;
import ua.passporttrainer.model.QuizSessionLifecycle;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingProductionTest {

    private static final Path PRODUCTION_CATALOGUE =
            Path.of("data", "passport-catalogue", "passport-catalogue.sqlite");
    private static final Path PRODUCTION_ASSETS = Path.of("Зразки паспортних документів");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void generatesPresentsAnswersAndCompletesAReadOnlyProductionTrainingSession() throws Exception {
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(PRODUCTION_CATALOGUE);
        AtomicLong sessionIds = new AtomicLong(500L);
        AtomicLong seeds = new AtomicLong(20260923L);
        QuizSessionCoordinator coordinator = new QuizSessionCoordinator(
                new GeneratedQuestionSetProvider(context.quizGenerationService()),
                new QuizSessionLifecycleService(CLOCK),
                () -> QuizSessionCoordinator.ANONYMOUS_USER_ID,
                sessionIds::getAndIncrement,
                seeds::getAndIncrement
        );
        coordinator.configure(new QuizSessionConfiguration(
                QuizMode.TRAINING,
                4,
                4,
                Set.of(QuizQuestionType.values()),
                Set.of(),
                Set.of(),
                Set.of()
        ));

        QuizSession generated = coordinator.start();
        Set<String> eligibleDocumentIds = new HashSet<>();
        context.learningService().findEligibleEntities().forEach(entity ->
                context.learningService().findEligibleDocuments(entity.entityId()).forEach(document ->
                        eligibleDocumentIds.add(document.documentId())
                )
        );
        FileSystemPassportImageLocator imageLocator = new FileSystemPassportImageLocator(PRODUCTION_ASSETS);
        generated.questions().stream()
                .flatMap(question -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(question.subject()),
                        question.options().stream()
                ))
                .filter(QuizContent.Document.class::isInstance)
                .map(QuizContent.Document.class::cast)
                .forEach(document -> {
                    assertTrue(eligibleDocumentIds.contains(document.id()));
                    assertTrue(Files.isReadable(Path.of(imageLocator.locate(document.imageRelativePath()))));
                });

        int expectedCorrect = 0;
        for (int index = 0; index < generated.questions().size(); index++) {
            QuizQuestion question = coordinator.currentQuestion();
            String answerId;
            if (index % 2 == 0) {
                answerId = question.correctAnswerId();
                expectedCorrect++;
            } else {
                answerId = question.options().stream()
                        .map(QuizContent::id)
                        .filter(id -> !id.equals(question.correctAnswerId()))
                        .findFirst()
                        .orElseThrow();
            }
            QuizAnswerSubmissionResult.Feedback feedback =
                    (QuizAnswerSubmissionResult.Feedback) coordinator.submitAnswer(answerId);
            assertEquals(index % 2 == 0, feedback.correct());
            if (index + 1 < generated.questions().size()) {
                coordinator.advance();
            }
        }
        QuizSession completed = coordinator.complete();

        int correctCount = expectedCorrect;
        assertAll(
                () -> assertEquals(12, eligibleDocumentIds.size()),
                () -> assertEquals(QuizSessionLifecycle.COMPLETED, coordinator.lifecycle().orElseThrow()),
                () -> assertTrue(completed.completed()),
                () -> assertEquals(4, completed.answers().size()),
                () -> assertEquals(correctCount, coordinator.visibleScore().orElseThrow()),
                () -> assertFalse(coordinator.randomSeed().isEmpty())
        );
    }
}
