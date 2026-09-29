package ua.passporttrainer.repository;

import java.util.List;
import java.util.Optional;

import ua.passporttrainer.model.QuestionVersion;

/** Append/read boundary for immutable authored question versions. */
public interface QuestionVersionRepository {

    QuestionVersion append(QuestionVersion version);

    Optional<QuestionVersion> findById(String versionId);

    List<QuestionVersion> findByQuestionId(String questionId);
}
