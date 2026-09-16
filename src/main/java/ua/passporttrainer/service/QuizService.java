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

/** Coordinates quiz-session lifecycle after questions have been generated. */
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

    public QuizSession submitAnswer(QuizSession session, long questionId, String selectedAnswerId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(selectedAnswerId, "selectedAnswerId");
        if (session.completed()) {
            throw new IllegalStateException("Quiz session is already completed");
        }
        if (session.answers().stream().anyMatch(answer -> answer.questionId() == questionId)) {
            throw new IllegalStateException("Question already answered: " + questionId);
        }

        QuizQuestion question = session.questions().stream()
                .filter(candidate -> candidate.id() == questionId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown question: " + questionId));
        if (!question.hasOption(selectedAnswerId)) {
            throw new IllegalArgumentException("Unknown answer option: " + selectedAnswerId);
        }

        QuizAnswer answer = new QuizAnswer(
                questionId,
                selectedAnswerId,
                question.correctAnswerId().equals(selectedAnswerId),
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
        if (session.completed()) {
            throw new IllegalStateException("Quiz session is already completed");
        }

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
        if (completedSession.mode() != QuizMode.EXAMINATION) {
            throw new IllegalArgumentException("Quiz session is not an examination");
        }
        if (!completedSession.completed()) {
            throw new IllegalStateException("Quiz session is not completed");
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
