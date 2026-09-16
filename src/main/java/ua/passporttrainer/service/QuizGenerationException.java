package ua.passporttrainer.service;

import java.util.Optional;

import ua.passporttrainer.model.QuizGenerationFailureReason;
import ua.passporttrainer.model.QuizQuestionType;

/** Expected, structured failure to satisfy a quiz-generation request. */
public final class QuizGenerationException extends RuntimeException {

    private final QuizGenerationFailureReason reason;
    private final int requestedQuestionCount;
    private final int availableCandidateCount;
    private final QuizQuestionType questionType;

    QuizGenerationException(
            QuizGenerationFailureReason reason,
            String detail,
            int requestedQuestionCount,
            int availableCandidateCount,
            QuizQuestionType questionType
    ) {
        super(detail);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
        this.requestedQuestionCount = requestedQuestionCount;
        this.availableCandidateCount = availableCandidateCount;
        this.questionType = questionType;
    }

    public QuizGenerationFailureReason reason() {
        return reason;
    }

    public int requestedQuestionCount() {
        return requestedQuestionCount;
    }

    public int availableCandidateCount() {
        return availableCandidateCount;
    }

    public Optional<QuizQuestionType> questionType() {
        return Optional.ofNullable(questionType);
    }
}
