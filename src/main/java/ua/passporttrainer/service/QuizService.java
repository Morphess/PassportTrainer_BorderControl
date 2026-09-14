package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.model.QuizAnswer;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.repository.ResultRepository;

/**
 * Coordinates quiz session state. Question generation rules are intentionally
 * deferred to a later development stage.
 */
public final class QuizService {

    private final ResultRepository resultRepository;
    private final Clock clock;

    public QuizService(ResultRepository resultRepository, Clock clock) {
        this.resultRepository = Objects.requireNonNull(resultRepository, "resultRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public QuizSession startSession(
            long sessionId,
            long userId,
            QuizMode mode,
            List<QuizQuestion> questions
    ) {
        return new QuizSession(
                sessionId,
                userId,
                Objects.requireNonNull(mode, "mode"),
                clock.instant(),
                null,
                questions,
                List.of()
        );
    }

    public QuizSession submitAnswer(QuizSession session, long questionId, String selectedAnswer) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(selectedAnswer, "selectedAnswer");

        QuizQuestion question = session.questions().stream()
                .filter(candidate -> candidate.id() == questionId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown question: " + questionId));

        QuizAnswer answer = new QuizAnswer(
                questionId,
                selectedAnswer,
                question.correctAnswer().equals(selectedAnswer),
                clock.instant()
        );
        List<QuizAnswer> answers = new ArrayList<>(session.answers());
        answers.add(answer);

        return new QuizSession(
                session.id(),
                session.userId(),
                session.mode(),
                session.startedAt(),
                session.completedAt(),
                session.questions(),
                answers
        );
    }

    public QuizSession completeSession(QuizSession session) {
        Objects.requireNonNull(session, "session");

        return new QuizSession(
                session.id(),
                session.userId(),
                session.mode(),
                session.startedAt(),
                clock.instant(),
                session.questions(),
                session.answers()
        );
    }

    public ExamResult saveExamResult(long resultId, QuizSession completedSession) {
        Objects.requireNonNull(completedSession, "completedSession");
        if (!completedSession.completed()) {
            throw new IllegalArgumentException("Quiz session is not completed");
        }

        int correctAnswers = (int) completedSession.answers().stream()
                .filter(QuizAnswer::correct)
                .count();
        long durationSeconds = Math.max(
                0,
                Duration.between(
                        completedSession.startedAt(),
                        completedSession.completedAt()
                ).toSeconds()
        );
        ExamResult result = new ExamResult(
                resultId,
                completedSession.userId(),
                completedSession.id(),
                correctAnswers,
                completedSession.questions().size(),
                durationSeconds,
                completedSession.completedAt()
        );
        return resultRepository.save(result);
    }
}
