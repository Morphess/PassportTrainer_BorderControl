package ua.passporttrainer.service;

import java.time.Duration;
import java.util.Objects;

import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.model.QuizAnswer;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.repository.ResultRepository;

/** Creates and persists results only for fully answered, completed examinations. */
public final class ExamResultService {

    private final ResultRepository resultRepository;

    public ExamResultService(ResultRepository resultRepository) {
        this.resultRepository = Objects.requireNonNull(resultRepository, "resultRepository");
    }

    public ExamResult saveExamResult(long resultId, QuizSession completedSession) {
        Objects.requireNonNull(completedSession, "completedSession");
        if (completedSession.mode() != QuizMode.EXAMINATION) {
            throw new IllegalArgumentException("Quiz session is not an examination");
        }
        if (!completedSession.completed()) {
            throw new IllegalStateException("Quiz session is not completed");
        }
        if (completedSession.answers().size() != completedSession.questions().size()) {
            throw new IllegalStateException("Examination contains unanswered questions");
        }

        int correctAnswers = 0;
        for (QuizAnswer answer : completedSession.answers()) {
            if (answer.correct()) {
                correctAnswers++;
            }
        }
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
