package ua.passporttrainer.service;

import ua.passporttrainer.model.QuestionSet;
import ua.passporttrainer.model.QuizGenerationRequest;

/** Supplies one validated ordered question set independently of session lifecycle ownership. */
@FunctionalInterface
public interface QuestionSetProvider {

    QuestionSet provide(QuizGenerationRequest request);
}
