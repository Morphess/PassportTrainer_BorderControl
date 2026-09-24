package ua.passporttrainer.presentation;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;

import ua.passporttrainer.service.QuizGenerationException;

/** Localizes structured generation failures without exposing internal exception details. */
public final class TrainingGenerationFailureFormatter {

    private final ResourceBundle resources;

    public TrainingGenerationFailureFormatter(ResourceBundle resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public String format(QuizGenerationException failure) {
        Objects.requireNonNull(failure, "failure");
        return switch (failure.reason()) {
            case INVALID_REQUEST -> resources.getString("training.failure.invalidRequest");
            case INSUFFICIENT_ELIGIBLE_DATA -> MessageFormat.format(
                    resources.getString("training.failure.insufficientEligibleData"),
                    failure.requestedQuestionCount(),
                    failure.availableCandidateCount()
            );
            case INSUFFICIENT_DISTRACTORS -> MessageFormat.format(
                    resources.getString("training.failure.insufficientDistractors"),
                    failure.questionType()
                            .map(type -> resources.getString("questionType." + type.name()))
                            .orElse(resources.getString("training.failure.unknownQuestionType")),
                    failure.requestedQuestionCount(),
                    failure.availableCandidateCount()
            );
        };
    }
}
