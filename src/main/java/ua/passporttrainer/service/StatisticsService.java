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

    public OptionalDouble averageScorePercent(long userId) {
        return resultRepository.findByUserId(userId).stream()
                .mapToDouble(ExamResult::scorePercent)
                .average();
    }
}
