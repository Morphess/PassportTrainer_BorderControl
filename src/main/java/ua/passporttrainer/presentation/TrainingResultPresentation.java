package ua.passporttrainer.presentation;

/** Derived in-memory Training result for the completed-session screen. */
public record TrainingResultPresentation(
        int correctCount,
        int incorrectCount,
        int totalCount,
        int percentage
) {

    public TrainingResultPresentation {
        if (totalCount <= 0) {
            throw new IllegalArgumentException("totalCount must be positive");
        }
        if (correctCount < 0 || correctCount > totalCount) {
            throw new IllegalArgumentException("correctCount is outside the result");
        }
        if (incorrectCount != totalCount - correctCount) {
            throw new IllegalArgumentException("incorrectCount does not match the total");
        }
        int expectedPercentage = (int) Math.round(correctCount * 100.0 / totalCount);
        if (percentage != expectedPercentage) {
            throw new IllegalArgumentException("percentage does not match the counts");
        }
    }

    public static TrainingResultPresentation fromCounts(int correctCount, int totalCount) {
        return new TrainingResultPresentation(
                correctCount,
                totalCount - correctCount,
                totalCount,
                (int) Math.round(correctCount * 100.0 / totalCount)
        );
    }
}
