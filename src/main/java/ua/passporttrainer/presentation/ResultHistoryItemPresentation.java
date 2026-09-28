package ua.passporttrainer.presentation;

import java.util.Objects;

/** Localized, UI-safe fields for one completed Examination result row. */
public record ResultHistoryItemPresentation(
        String completedAt,
        String assessment,
        String totalQuestions,
        String correctAnswers,
        String percentage,
        String duration
) {

    public ResultHistoryItemPresentation {
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        assessment = Objects.requireNonNull(assessment, "assessment");
        totalQuestions = Objects.requireNonNull(totalQuestions, "totalQuestions");
        correctAnswers = Objects.requireNonNull(correctAnswers, "correctAnswers");
        percentage = Objects.requireNonNull(percentage, "percentage");
        duration = Objects.requireNonNull(duration, "duration");
    }
}
