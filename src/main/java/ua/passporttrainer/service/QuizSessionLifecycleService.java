package ua.passporttrainer.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ua.passporttrainer.model.QuizAnswer;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizSession;

/** Applies answer and completion invariants to an in-memory quiz session. */
public final class QuizSessionLifecycleService {

    private final Clock clock;

    public QuizSessionLifecycleService(Clock clock) {
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
        for (QuizAnswer answer : session.answers()) {
            if (answer.questionId() == questionId) {
                throw new IllegalStateException("Question already answered: " + questionId);
            }
        }

        QuizQuestion question = null;
        for (QuizQuestion candidate : session.questions()) {
            if (candidate.id() == questionId) {
                question = candidate;
                break;
            }
        }
        if (question == null) {
            throw new IllegalArgumentException("Unknown question: " + questionId);
        }
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
        if (session.answers().size() != session.questions().size()) {
            throw new IllegalStateException("Every question must be answered before completion");
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
}
