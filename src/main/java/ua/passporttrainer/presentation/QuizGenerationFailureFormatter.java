package ua.passporttrainer.presentation;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;

import ua.passporttrainer.service.QuizGenerationException;
import ua.passporttrainer.model.QuizMode;

/** Localizes structured generation failures without exposing internal exception details. */
public final class QuizGenerationFailureFormatter {

    private final ResourceBundle resources;
    private final String keyPrefix;

    public QuizGenerationFailureFormatter(ResourceBundle resources, QuizMode mode) {
        this.resources = Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(mode, "mode");
        this.keyPrefix = switch (mode) {
            case TRAINING -> "training.failure.";
            case EXAMINATION -> "examination.failure.";
            case LEARNING -> throw new IllegalArgumentException(
                    "Learning does not generate quiz sessions"
            );
        };
    }

    public String format(QuizGenerationException failure) {
        Objects.requireNonNull(failure, "failure");
        return switch (failure.reason()) {
            case INVALID_REQUEST -> resources.getString(keyPrefix + "invalidRequest");
            case INSUFFICIENT_ELIGIBLE_DATA -> MessageFormat.format(
                    resources.getString(keyPrefix + "insufficientEligibleData"),
                    failure.requestedQuestionCount(),
                    failure.availableCandidateCount()
            );
            case INSUFFICIENT_DISTRACTORS -> MessageFormat.format(
                    resources.getString(keyPrefix + "insufficientDistractors"),
                    failure.questionType()
                            .map(type -> resources.getString("questionType." + type.name()))
                            .orElse(resources.getString(keyPrefix + "unknownQuestionType")),
                    failure.requestedQuestionCount(),
                    failure.availableCandidateCount()
            );
        };
    }
}
