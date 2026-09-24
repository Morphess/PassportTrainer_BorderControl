package ua.passporttrainer.service;

import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.model.AssessmentProvenance;
import ua.passporttrainer.model.AssessmentSource;
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

    public ExamResult saveExamResult(
            long resultId,
            QuizSession completedSession,
            AssessmentSource assessmentSource,
            Optional<AssessmentProvenance> assessmentProvenance
    ) {
        Objects.requireNonNull(completedSession, "completedSession");
        Objects.requireNonNull(assessmentSource, "assessmentSource");
        Objects.requireNonNull(assessmentProvenance, "assessmentProvenance");
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
        Long userId = completedSession.userId() == QuizSessionCoordinator.ANONYMOUS_USER_ID
                ? null
                : completedSession.userId();
        ExamResult result = new ExamResult(
                resultId,
                userId,
                completedSession.id(),
                assessmentSource,
                assessmentProvenance,
                completedSession.startedAt(),
                completedSession.completedAt(),
                correctAnswers,
                completedSession.questions().size()
        );
        return resultRepository.save(result);
    }
}
