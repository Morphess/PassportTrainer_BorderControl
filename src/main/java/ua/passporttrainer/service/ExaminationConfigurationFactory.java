package ua.passporttrainer.service;

import java.util.Set;

import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSessionConfiguration;

/** Creates the fixed generated Examination policy used by the initial UI flow. */
public final class ExaminationConfigurationFactory {

    public static final int QUESTION_COUNT = 10;
    public static final int OPTION_COUNT = 4;

    public QuizSessionConfiguration create() {
        return new QuizSessionConfiguration(
                QuizMode.EXAMINATION,
                QUESTION_COUNT,
                OPTION_COUNT,
                Set.of(QuizQuestionType.values()),
                Set.of(),
                Set.of(),
                Set.of()
        );
    }
}
