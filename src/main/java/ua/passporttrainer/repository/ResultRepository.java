package ua.passporttrainer.repository;

import java.util.List;
import java.util.Optional;

import ua.passporttrainer.model.ExamResult;

public interface ResultRepository {

    ExamResult save(ExamResult result);

    Optional<ExamResult> findById(long id);

    List<ExamResult> findByUserId(long userId);
}
