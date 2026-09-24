package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.AssessmentProvenance;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.model.QuizCandidate;
import ua.passporttrainer.model.QuestionSet;
import ua.passporttrainer.model.QuizContent;
import ua.passporttrainer.model.QuizGenerationFailureReason;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.model.QuizSessionConfiguration;
import ua.passporttrainer.model.QuizSessionLifecycle;
import ua.passporttrainer.model.ReviewStatus;
import ua.passporttrainer.repository.PassportRepository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizSessionCoordinatorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC
    );

    private CountingPassportRepository repository;
    private QuizSessionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        repository = new CountingPassportRepository(candidates());
        AtomicLong sessionIds = new AtomicLong(100);
        long[] seeds = {11L, 22L, 33L};
        AtomicInteger seedIndex = new AtomicInteger();
        coordinator = coordinator(
                repository,
                sessionIds::getAndIncrement,
                () -> seeds[seedIndex.getAndIncrement()]
        );
    }

    @Test
    void configuredSessionMovesFromCreatedToActive() {
        coordinator.configure(configuration(QuizMode.TRAINING));

        QuizSession session = coordinator.start();

        assertAll(
                () -> assertEquals(QuizSessionLifecycle.ACTIVE, coordinator.lifecycle().orElseThrow()),
                () -> assertEquals(100, session.id()),
                () -> assertEquals(QuizSessionCoordinator.ANONYMOUS_USER_ID, session.userId()),
                () -> assertEquals(0, coordinator.currentQuestionIndex().orElseThrow()),
                () -> assertEquals(11, coordinator.randomSeed().orElseThrow()),
                () -> assertEquals(1, repository.quizCandidateReads)
        );
    }
    @Test
    void startsLifecycleSessionFromProviderQuestionsAndRetainsProvenance() {
        AtomicInteger providerCalls = new AtomicInteger();
        QuizQuestion suppliedQuestion = question(91L);
        AssessmentProvenance provenance = new AssessmentProvenance(
                "assessment:future",
                "version:1"
        );
        QuestionSetProvider provider = request -> {
            providerCalls.incrementAndGet();
            assertEquals(QuizMode.EXAMINATION, request.mode());
            assertEquals(44L, request.randomSeed());
            return new QuestionSet(List.of(suppliedQuestion), Optional.of(provenance));
        };
        QuizSessionCoordinator isolated = new QuizSessionCoordinator(
                provider,
                new QuizSessionLifecycleService(CLOCK),
                () -> 700L,
                () -> 44L
        );
        isolated.configure(new QuizSessionConfiguration(
                QuizMode.EXAMINATION,
                1,
                2,
                Set.of(QuizQuestionType.DOCUMENT_TYPE),
                Set.of(),
                Set.of(),
                Set.of()
        ));

        QuizSession session = isolated.start();

        assertAll(
                () -> assertEquals(1, providerCalls.get()),
                () -> assertEquals(700L, session.id()),
                () -> assertEquals(CLOCK.instant(), session.startedAt()),
                () -> assertEquals(List.of(suppliedQuestion), session.questions()),
                () -> assertEquals(Optional.of(provenance), isolated.assessmentProvenance())
        );
    }


    @Test
    void generationFailureRemainsCreatedWithoutPartialState() {
        CountingPassportRepository emptyRepository = new CountingPassportRepository(List.of());
        QuizSessionCoordinator failing = coordinator(emptyRepository, () -> 1L, () -> 2L);
        failing.configure(configuration(QuizMode.TRAINING));

        QuizGenerationException exception = assertThrows(
                QuizGenerationException.class,
                failing::start
        );

        assertAll(
                () -> assertEquals(
                        QuizGenerationFailureReason.INSUFFICIENT_ELIGIBLE_DATA,
                        exception.reason()
                ),
                () -> assertEquals(QuizSessionLifecycle.CREATED, failing.lifecycle().orElseThrow()),
                () -> assertTrue(failing.session().isEmpty()),
                () -> assertTrue(failing.currentQuestionIndex().isEmpty()),
                () -> assertTrue(failing.randomSeed().isEmpty()),
                () -> assertEquals(1, emptyRepository.quizCandidateReads)
        );
    }

    @Test
    void cannotAdvanceBeforeCurrentQuestionIsAnswered() {
        startTraining();

        assertThrows(IllegalStateException.class, coordinator::advance);
        assertEquals(0, coordinator.currentQuestionIndex().orElseThrow());
    }

    @Test
    void advancesOnlyAfterCurrentQuestionIsAnswered() {
        startTraining();
        QuizQuestion first = coordinator.currentQuestion();
        coordinator.submitAnswer(first.correctAnswerId());

        QuizQuestion second = coordinator.advance();

        assertAll(
                () -> assertEquals(1, coordinator.currentQuestionIndex().orElseThrow()),
                () -> assertFalse(first.id() == second.id())
        );
    }

    @Test
    void duplicateAnswerIsRejectedBelowThePresentationLayer() {
        startTraining();
        String answerId = coordinator.currentQuestion().correctAnswerId();
        coordinator.submitAnswer(answerId);

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.submitAnswer(answerId)
        );
    }

    @Test
    void derivesProgressAndTrainingScore() {
        startTraining();
        coordinator.submitAnswer(coordinator.currentQuestion().correctAnswerId());

        QuizSessionProgress progress = coordinator.progress();

        assertAll(
                () -> assertEquals(1, progress.currentQuestionNumber()),
                () -> assertEquals(2, progress.totalQuestions()),
                () -> assertEquals(1, progress.answeredCount()),
                () -> assertEquals(1, progress.remainingCount()),
                () -> assertFalse(progress.completionReady()),
                () -> assertEquals(1, coordinator.visibleScore().orElseThrow())
        );
    }

    @Test
    void completesOnlyAfterAllQuestionsAreAnswered() {
        startTraining();
        coordinator.submitAnswer(coordinator.currentQuestion().correctAnswerId());

        assertThrows(IllegalStateException.class, coordinator::complete);

        coordinator.advance();
        coordinator.submitAnswer(wrongAnswer(coordinator.currentQuestion()));
        QuizSession completed = coordinator.complete();

        assertAll(
                () -> assertEquals(QuizSessionLifecycle.COMPLETED, coordinator.lifecycle().orElseThrow()),
                () -> assertTrue(completed.completed()),
                () -> assertEquals(CLOCK.instant(), completed.completedAt()),
                () -> assertEquals(1, coordinator.visibleScore().orElseThrow())
        );
    }

    @Test
    void trainingSubmissionReturnsAuthoritativeFeedback() {
        startTraining();
        QuizQuestion question = coordinator.currentQuestion();

        QuizAnswerSubmissionResult result = coordinator.submitAnswer(question.correctAnswerId());
        QuizAnswerSubmissionResult.Feedback feedback = assertInstanceOf(
                QuizAnswerSubmissionResult.Feedback.class,
                result
        );

        assertAll(
                () -> assertTrue(feedback.correct()),
                () -> assertEquals(question.correctAnswerId(), feedback.correctOptionId()),
                () -> assertEquals(1, feedback.answeredCount()),
                () -> assertEquals(1, feedback.remainingCount())
        );
    }

    @Test
    void examinationHidesFeedbackAndScoreUntilValidCompletion() {
        coordinator.configure(configuration(QuizMode.EXAMINATION));
        QuizSession active = coordinator.start();
        assertNull(active.completedAt());

        QuizQuestion firstQuestion = coordinator.currentQuestion();
        QuizAnswerSubmissionResult firstResult = coordinator.submitAnswer(
                firstQuestion.correctAnswerId()
        );

        assertAll(
                () -> assertInstanceOf(QuizAnswerSubmissionResult.Accepted.class, firstResult),
                () -> assertFalse(firstResult instanceof QuizAnswerSubmissionResult.Feedback),
                () -> assertTrue(coordinator.visibleScore().isEmpty()),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> coordinator.submitAnswer(firstQuestion.correctAnswerId())
                ),
                () -> assertThrows(IllegalStateException.class, coordinator::complete),
                () -> assertNull(coordinator.session().orElseThrow().completedAt())
        );

        coordinator.advance();
        QuizAnswerSubmissionResult finalResult = coordinator.submitAnswer(
                wrongAnswer(coordinator.currentQuestion())
        );
        QuizAnswerSubmissionResult.Accepted accepted = assertInstanceOf(
                QuizAnswerSubmissionResult.Accepted.class,
                finalResult
        );
        assertAll(
                () -> assertTrue(accepted.completionReady()),
                () -> assertTrue(coordinator.visibleScore().isEmpty())
        );

        QuizSession completed = coordinator.complete();

        assertAll(
                () -> assertEquals(QuizSessionLifecycle.COMPLETED, coordinator.lifecycle().orElseThrow()),
                () -> assertEquals(2, completed.answers().size()),
                () -> assertEquals(CLOCK.instant(), completed.completedAt()),
                () -> assertEquals(1, coordinator.visibleScore().orElseThrow()),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> coordinator.submitAnswer(firstQuestion.correctAnswerId())
                )
        );
    }

    @Test
    void restartUsesNewIdAndSeedWithoutPreviousAnswers() {
        QuizSession first = startTraining();
        coordinator.submitAnswer(coordinator.currentQuestion().correctAnswerId());

        QuizSession restarted = coordinator.restart();

        assertAll(
                () -> assertEquals(101, restarted.id()),
                () -> assertFalse(first.id() == restarted.id()),
                () -> assertEquals(22, coordinator.randomSeed().orElseThrow()),
                () -> assertTrue(restarted.answers().isEmpty()),
                () -> assertEquals(0, coordinator.currentQuestionIndex().orElseThrow()),
                () -> assertEquals(2, repository.quizCandidateReads)
        );
    }

    @Test
    void abandonClearsGeneratedStateAndRetainsAcceptedConfiguration() {
        QuizSessionConfiguration configuration = configuration(QuizMode.TRAINING);
        coordinator.configure(configuration);
        coordinator.start();
        coordinator.submitAnswer(coordinator.currentQuestion().correctAnswerId());

        coordinator.abandon();

        assertAll(
                () -> assertEquals(configuration, coordinator.configuration().orElseThrow()),
                () -> assertEquals(QuizSessionLifecycle.CREATED, coordinator.lifecycle().orElseThrow()),
                () -> assertTrue(coordinator.session().isEmpty()),
                () -> assertTrue(coordinator.currentQuestionIndex().isEmpty()),
                () -> assertTrue(coordinator.randomSeed().isEmpty()),
                () -> assertTrue(coordinator.visibleScore().isEmpty())
        );
    }

    @Test
    void configurationDefensivelyCopiesEveryScope() {
        Set<QuizQuestionType> questionTypes = new java.util.HashSet<>();
        questionTypes.add(QuizQuestionType.DOCUMENT_TYPE);
        Set<String> continents = new java.util.HashSet<>();
        Set<String> entities = new java.util.HashSet<>();
        Set<DocumentType> documentTypes = new java.util.HashSet<>();
        QuizSessionConfiguration configuration = new QuizSessionConfiguration(
                QuizMode.TRAINING,
                2,
                2,
                questionTypes,
                continents,
                entities,
                documentTypes
        );

        questionTypes.clear();
        continents.add("continent:other");
        entities.add("entity:other");
        documentTypes.add(DocumentType.UNKNOWN);

        assertAll(
                () -> assertEquals(Set.of(QuizQuestionType.DOCUMENT_TYPE), configuration.allowedQuestionTypes()),
                () -> assertTrue(configuration.continentIds().isEmpty()),
                () -> assertTrue(configuration.entityIds().isEmpty()),
                () -> assertTrue(configuration.documentTypes().isEmpty())
        );
    }

    private QuizSession startTraining() {
        coordinator.configure(configuration(QuizMode.TRAINING));
        return coordinator.start();
    }

    private static QuizSessionConfiguration configuration(QuizMode mode) {
        return new QuizSessionConfiguration(
                mode,
                2,
                2,
                Set.of(QuizQuestionType.DOCUMENT_TYPE),
                Set.of(),
                Set.of(),
                Set.of()
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

    private static String wrongAnswer(QuizQuestion question) {
        return question.options().stream()
                .map(option -> option.id())
                .filter(id -> !id.equals(question.correctAnswerId()))
                .findFirst()
                .orElseThrow();
    }

    private static QuizSessionCoordinator coordinator(
            CountingPassportRepository repository,
            LongSupplier sessionIds,
            LongSupplier seeds
    ) {
        QuizGenerationService generationService = new QuizGenerationService(repository, CLOCK);
        return new QuizSessionCoordinator(
                new GeneratedQuestionSetProvider(generationService),
                new QuizSessionLifecycleService(CLOCK),
                sessionIds,
                seeds
        );
    }

    private static List<QuizCandidate> candidates() {
        return List.of(
                candidate(1, DocumentType.ORDINARY_PASSPORT),
                candidate(2, DocumentType.DIPLOMATIC_PASSPORT),
                candidate(3, DocumentType.SERVICE_PASSPORT)
        );
    }

    private static QuizCandidate candidate(int id, DocumentType type) {
        String documentId = "document:" + id;
        String imageId = "image:" + id;
        return new QuizCandidate(
                documentId,
                "entity:" + id,
                "Entity " + id,
                Set.of("continent:europe"),
                type,
                "Document " + id,
                imageId,
                "fixtures/" + id + ".jpg",
                "%064x".formatted(id),
                ReviewStatus.VERIFIED,
                ReviewStatus.VERIFIED,
                ReviewStatus.VERIFIED,
                true,
                documentId,
                imageId
        );
    }

    private static final class CountingPassportRepository implements PassportRepository {

        private final List<QuizCandidate> candidates;
        private int quizCandidateReads;

        private CountingPassportRepository(List<QuizCandidate> candidates) {
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public Optional<PassportDocument> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<PassportDocument> findByCountryId(String countryId) {
            return List.of();
        }

        @Override
        public List<PassportImage> findImagesByDocumentId(String passportDocumentId) {
            return List.of();
        }

        @Override
        public List<QuizCandidate> findQuizEligibleCandidates() {
            quizCandidateReads++;
            return candidates;
        }
    }
}
