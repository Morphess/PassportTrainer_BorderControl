package ua.passporttrainer.service;

import java.util.Objects;
import java.util.OptionalDouble;

/** Derived, non-persisted statistics for one user's completed examinations. */
public record UserResultStatistics(
        long completedExaminations,
        OptionalDouble averagePercentage,
        OptionalDouble bestPercentage,
        long totalAnswered,
        long totalCorrect
) {

    public UserResultStatistics {
        averagePercentage = Objects.requireNonNull(
                averagePercentage, "averagePercentage"
        );
        bestPercentage = Objects.requireNonNull(bestPercentage, "bestPercentage");
        if (completedExaminations < 0 || totalAnswered < 0 || totalCorrect < 0) {
            throw new IllegalArgumentException("Statistics counts must not be negative");
        }
        if (totalCorrect > totalAnswered) {
            throw new IllegalArgumentException("totalCorrect must not exceed totalAnswered");
        }
        if (completedExaminations == 0
                && (averagePercentage.isPresent() || bestPercentage.isPresent())) {
            throw new IllegalArgumentException("Empty statistics must not contain percentages");
        }
        if (completedExaminations > 0
                && (averagePercentage.isEmpty() || bestPercentage.isEmpty())) {
            throw new IllegalArgumentException("Non-empty statistics require percentages");
        }
    }
}
