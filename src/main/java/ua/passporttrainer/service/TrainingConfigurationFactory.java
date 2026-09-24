package ua.passporttrainer.service;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSessionConfiguration;

/** Converts accepted Training controls to the immutable coordinator configuration. */
public final class TrainingConfigurationFactory {

    public static final int DEFAULT_QUESTION_COUNT = 5;
    public static final int OPTION_COUNT = 4;

    public QuizSessionConfiguration defaultConfiguration() {
        return create(
                DEFAULT_QUESTION_COUNT,
                EnumSet.allOf(QuizQuestionType.class),
                Optional.empty()
        );
    }

    public QuizSessionConfiguration create(
            int questionCount,
            Set<QuizQuestionType> questionTypes,
            Optional<String> entityId
    ) {
        Objects.requireNonNull(questionTypes, "questionTypes");
        Objects.requireNonNull(entityId, "entityId");
        if (questionCount <= 0) {
            throw new IllegalArgumentException("questionCount must be positive");
        }
        if (questionTypes.isEmpty()) {
            throw new IllegalArgumentException("questionTypes must not be empty");
        }
        entityId.ifPresent(id -> {
            if (id.isBlank()) {
                throw new IllegalArgumentException("entityId must not be blank");
            }
        });
        return new QuizSessionConfiguration(
                QuizMode.TRAINING,
                questionCount,
                OPTION_COUNT,
                questionTypes,
                Set.of(),
                entityId.map(Set::of).orElseGet(Set::of),
                Set.of()
        );
    }
}
