package ua.passporttrainer.repository;

import java.util.List;
import java.util.Optional;

import ua.passporttrainer.model.AssessmentDefinition;

/** Persistence boundary for the minimal authored assessment aggregate. */
public interface AssessmentRepository {

    AssessmentDefinition create(AssessmentDefinition assessment);

    AssessmentDefinition update(AssessmentDefinition assessment);

    Optional<AssessmentDefinition> findById(String assessmentId);

    List<AssessmentDefinition> findAll();
}
