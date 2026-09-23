package ua.passporttrainer.service;

import java.util.Objects;

import ua.passporttrainer.model.QuizMode;

/** Mode-specific disclosure policy independent of JavaFX presentation. */
public record QuizModePolicy(
        boolean immediateCorrectnessFeedback,
        boolean scoreAvailableDuringSession,
        boolean scoreAvailableAfterCompletion
) {
    private static final QuizModePolicy LEARNING_POLICY =
            new QuizModePolicy(true, false, false);
    private static final QuizModePolicy TRAINING_POLICY =
            new QuizModePolicy(true, true, true);
    private static final QuizModePolicy EXAMINATION_POLICY =
            new QuizModePolicy(false, false, true);

    public static QuizModePolicy forMode(QuizMode mode) {
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case LEARNING -> LEARNING_POLICY;
            case TRAINING -> TRAINING_POLICY;
            case EXAMINATION -> EXAMINATION_POLICY;
        };
    }
}
