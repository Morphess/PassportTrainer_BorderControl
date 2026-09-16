package ua.passporttrainer.model;

/** Stable identities for expected quiz-generation failures. */
public enum QuizGenerationFailureReason {
    INVALID_REQUEST,
    INSUFFICIENT_ELIGIBLE_DATA,
    INSUFFICIENT_DISTRACTORS
}
