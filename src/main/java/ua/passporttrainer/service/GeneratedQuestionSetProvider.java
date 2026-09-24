package ua.passporttrainer.service;

import java.util.Objects;

import ua.passporttrainer.model.QuestionSet;
import ua.passporttrainer.model.QuizGenerationRequest;

/** Supplies generated questions through the existing canonical generation rules. */
public final class GeneratedQuestionSetProvider implements QuestionSetProvider {

    private final QuizGenerationService generationService;

    public GeneratedQuestionSetProvider(QuizGenerationService generationService) {
        this.generationService = Objects.requireNonNull(generationService, "generationService");
    }

    @Override
    public QuestionSet provide(QuizGenerationRequest request) {
        return generationService.generateQuestionSet(request);
    }
}
