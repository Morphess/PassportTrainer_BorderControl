package ua.passporttrainer.presentation;

import java.util.Objects;

/** Localized, UI-safe values for personal Examination statistics. */
public record UserStatisticsPresentation(
        String completedExaminations,
        String averagePercentage,
        String bestPercentage,
        String totalAnswered,
        String totalCorrect
) {

    public UserStatisticsPresentation {
        completedExaminations = Objects.requireNonNull(
                completedExaminations, "completedExaminations"
        );
        averagePercentage = Objects.requireNonNull(
                averagePercentage, "averagePercentage"
        );
        bestPercentage = Objects.requireNonNull(bestPercentage, "bestPercentage");
        totalAnswered = Objects.requireNonNull(totalAnswered, "totalAnswered");
        totalCorrect = Objects.requireNonNull(totalCorrect, "totalCorrect");
    }
}
