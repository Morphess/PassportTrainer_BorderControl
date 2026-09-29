package ua.passporttrainer.repository;

import java.util.List;
import java.util.Optional;

import ua.passporttrainer.model.Question;

/** Persistence boundary for authored question aggregate identity and lifecycle. */
public interface QuestionRepository {

    Question create(Question question);

    Question update(Question question);

    Optional<Question> findById(String questionId);

    List<Question> findAll();
}
