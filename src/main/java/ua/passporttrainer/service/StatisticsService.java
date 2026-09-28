package ua.passporttrainer.service;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.repository.ResultRepository;

public final class StatisticsService {

    private final ResultRepository resultRepository;

    public StatisticsService(ResultRepository resultRepository) {
        this.resultRepository = Objects.requireNonNull(resultRepository, "resultRepository");
    }

    public List<ExamResult> findResults(long userId) {
        return List.copyOf(resultRepository.findByUserId(userId));
    }

    public UserResultsSnapshot findUserResults(long userId) {
        List<ExamResult> results = findResults(userId);
        long totalAnswered = 0;
        long totalCorrect = 0;
        double percentageSum = 0.0;
        double bestPercentage = Double.NEGATIVE_INFINITY;
        for (ExamResult result : results) {
            totalAnswered += result.totalQuestions();
            totalCorrect += result.correctAnswers();
            double percentage = result.scorePercent();
            percentageSum += percentage;
            bestPercentage = Math.max(bestPercentage, percentage);
        }
        OptionalDouble average = results.isEmpty()
                ? OptionalDouble.empty()
                : OptionalDouble.of(percentageSum / results.size());
        OptionalDouble best = results.isEmpty()
                ? OptionalDouble.empty()
                : OptionalDouble.of(bestPercentage);
        return new UserResultsSnapshot(
                results,
                new UserResultStatistics(
                        results.size(), average, best, totalAnswered, totalCorrect
                )
        );
    }

    public OptionalDouble averageScorePercent(long userId) {
        return findUserResults(userId).statistics().averagePercentage();
    }
}
