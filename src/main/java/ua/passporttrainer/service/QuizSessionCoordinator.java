package ua.passporttrainer.service;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

import ua.passporttrainer.model.AssessmentProvenance;
import ua.passporttrainer.model.QuizAnswer;
import ua.passporttrainer.model.QuizGenerationRequest;
import ua.passporttrainer.model.QuestionSet;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.model.QuizSessionConfiguration;
import ua.passporttrainer.model.QuizSessionLifecycle;

/** Owns the single in-memory quiz session configured by the application. */
public final class QuizSessionCoordinator {

    /** Temporary identity for non-persisted sessions before local user accounts exist. */
    public static final long ANONYMOUS_USER_ID = 0L;

    private final QuestionSetProvider questionSetProvider;
    private final QuizSessionLifecycleService lifecycleService;
    private final LongSupplier sessionIdSupplier;
    private final LongSupplier randomSeedSupplier;

    private QuizSessionConfiguration configuration;
    private QuizSessionLifecycle lifecycle;
    private QuizSession session;
    private int currentQuestionIndex = -1;
    private Long randomSeed;
    private AssessmentProvenance assessmentProvenance;

    public QuizSessionCoordinator(
            QuestionSetProvider questionSetProvider,
            QuizSessionLifecycleService lifecycleService,
            LongSupplier sessionIdSupplier,
            LongSupplier randomSeedSupplier
    ) {
        this.questionSetProvider = Objects.requireNonNull(questionSetProvider, "questionSetProvider");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
        this.sessionIdSupplier = Objects.requireNonNull(sessionIdSupplier, "sessionIdSupplier");
        this.randomSeedSupplier = Objects.requireNonNull(randomSeedSupplier, "randomSeedSupplier");
    }

    public void configure(QuizSessionConfiguration acceptedConfiguration) {
        Objects.requireNonNull(acceptedConfiguration, "acceptedConfiguration");
        if (lifecycle == QuizSessionLifecycle.ACTIVE) {
            throw new IllegalStateException("Cannot replace configuration during an active session");
        }
        configuration = acceptedConfiguration;
        clearGeneratedSession();
        lifecycle = QuizSessionLifecycle.CREATED;
    }

    public QuizSession start() {
        requireLifecycle(QuizSessionLifecycle.CREATED);
        long sessionId = sessionIdSupplier.getAsLong();
        long seed = randomSeedSupplier.getAsLong();
        QuizGenerationRequest request = new QuizGenerationRequest(
                sessionId,
                ANONYMOUS_USER_ID,
                configuration.mode(),
                configuration.questionCount(),
                configuration.optionCount(),
                configuration.allowedQuestionTypes(),
                configuration.continentIds(),
                configuration.entityIds(),
                configuration.documentTypes(),
                seed
        );

        QuestionSet questionSet = questionSetProvider.provide(request);
        validateQuestionSet(questionSet, request);
        QuizSession started = lifecycleService.startSession(
                sessionId,
                ANONYMOUS_USER_ID,
                configuration.mode(),
                questionSet.questions()
        );
        session = started;
        currentQuestionIndex = 0;
        randomSeed = seed;
        assessmentProvenance = questionSet.assessmentProvenance().orElse(null);
        lifecycle = QuizSessionLifecycle.ACTIVE;
        return started;
    }

    public QuizAnswerSubmissionResult submitAnswer(String selectedAnswerId) {
        QuizSession activeSession = requireActiveSession();
        QuizQuestion question = activeSession.questions().get(currentQuestionIndex);
        session = lifecycleService.submitAnswer(activeSession, question.id(), selectedAnswerId);
        QuizAnswer acceptedAnswer = session.answers().getLast();
        int answeredCount = session.answers().size();
        int remainingCount = session.questions().size() - answeredCount;
        boolean completionReady = remainingCount == 0;
        QuizModePolicy policy = QuizModePolicy.forMode(session.mode());
        if (policy.immediateCorrectnessFeedback()) {
            return new QuizAnswerSubmissionResult.Feedback(
                    acceptedAnswer.selectedAnswerId(),
                    acceptedAnswer.correct(),
                    question.correctAnswerId(),
                    answeredCount,
                    remainingCount,
                    completionReady
            );
        }
        return new QuizAnswerSubmissionResult.Accepted(
                acceptedAnswer.selectedAnswerId(),
                answeredCount,
                remainingCount,
                completionReady
        );
    }

    public QuizQuestion advance() {
        QuizSession activeSession = requireActiveSession();
        QuizQuestion current = activeSession.questions().get(currentQuestionIndex);
        boolean answered = false;
        for (QuizAnswer answer : activeSession.answers()) {
            if (answer.questionId() == current.id()) {
                answered = true;
                break;
            }
        }
        if (!answered) {
            throw new IllegalStateException("Current question must be answered before advancing");
        }
        if (currentQuestionIndex + 1 >= activeSession.questions().size()) {
            throw new IllegalStateException("The session has no next question");
        }
        currentQuestionIndex++;
        return activeSession.questions().get(currentQuestionIndex);
    }

    public QuizSession complete() {
        session = lifecycleService.completeSession(requireActiveSession());
        lifecycle = QuizSessionLifecycle.COMPLETED;
        return session;
    }

    public QuizSession restart() {
        if (session == null || lifecycle == QuizSessionLifecycle.CREATED) {
            throw new IllegalStateException("There is no generated session to restart");
        }
        clearGeneratedSession();
        lifecycle = QuizSessionLifecycle.CREATED;
        return start();
    }

    public void abandon() {
        clearGeneratedSession();
        lifecycle = configuration == null ? null : QuizSessionLifecycle.CREATED;
    }

    public QuizQuestion currentQuestion() {
        QuizSession activeSession = requireActiveSession();
        return activeSession.questions().get(currentQuestionIndex);
    }

    public QuizSessionProgress progress() {
        if (session == null || lifecycle == QuizSessionLifecycle.CREATED) {
            throw new IllegalStateException("No generated session is available");
        }
        int answeredCount = session.answers().size();
        int remainingCount = session.questions().size() - answeredCount;
        return new QuizSessionProgress(
                currentQuestionIndex + 1,
                session.questions().size(),
                answeredCount,
                remainingCount,
                remainingCount == 0
        );
    }

    public OptionalInt visibleScore() {
        if (session == null || lifecycle == QuizSessionLifecycle.CREATED) {
            return OptionalInt.empty();
        }
        QuizModePolicy policy = QuizModePolicy.forMode(session.mode());
        boolean visible = lifecycle == QuizSessionLifecycle.COMPLETED
                ? policy.scoreAvailableAfterCompletion()
                : policy.scoreAvailableDuringSession();
        if (!visible) {
            return OptionalInt.empty();
        }
        int score = 0;
        for (QuizAnswer answer : session.answers()) {
            if (answer.correct()) {
                score++;
            }
        }
        return OptionalInt.of(score);
    }

    public Optional<QuizSessionConfiguration> configuration() {
        return Optional.ofNullable(configuration);
    }

    public Optional<QuizSessionLifecycle> lifecycle() {
        return Optional.ofNullable(lifecycle);
    }

    public Optional<QuizSession> session() {
        return Optional.ofNullable(session);
    }

    public OptionalInt currentQuestionIndex() {
        return currentQuestionIndex < 0 ? OptionalInt.empty() : OptionalInt.of(currentQuestionIndex);
    }

    public OptionalLong randomSeed() {
        return randomSeed == null ? OptionalLong.empty() : OptionalLong.of(randomSeed);
    }

    public Optional<AssessmentProvenance> assessmentProvenance() {
        return Optional.ofNullable(assessmentProvenance);
    }

    private QuizSession requireActiveSession() {
        requireLifecycle(QuizSessionLifecycle.ACTIVE);
        return session;
    }

    private void requireLifecycle(QuizSessionLifecycle expected) {
        if (configuration == null || lifecycle != expected) {
            throw new IllegalStateException("Quiz session must be " + expected);
        }
    }

    private void clearGeneratedSession() {
        session = null;
        currentQuestionIndex = -1;
        randomSeed = null;
        assessmentProvenance = null;
    }

    private static void validateQuestionSet(
            QuestionSet questionSet,
            QuizGenerationRequest request
    ) {
        Objects.requireNonNull(questionSet, "questionSet");
        if (questionSet.questions().size() != request.questionCount()) {
            throw new IllegalStateException("Question provider returned an incompatible set");
        }
    }
}
