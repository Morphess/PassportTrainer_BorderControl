package ua.passporttrainer.service;

import java.util.List;
import java.util.Objects;

import ua.passporttrainer.model.ExamResult;

/** One authorized user's completed Examination history and derived statistics. */
public record UserResultsSnapshot(
        List<ExamResult> results,
        UserResultStatistics statistics
) {

    public UserResultsSnapshot {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        statistics = Objects.requireNonNull(statistics, "statistics");
        if (statistics.completedExaminations() != results.size()) {
            throw new IllegalArgumentException("Statistics count must match result history");
        }
    }
}
