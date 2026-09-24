package ua.passporttrainer.service;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSessionConfiguration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingConfigurationFactoryTest {

    private final TrainingConfigurationFactory factory = new TrainingConfigurationFactory();

    @Test
    void createsTheValidDefaultTrainingConfiguration() {
        QuizSessionConfiguration configuration = factory.defaultConfiguration();

        assertAll(
                () -> assertEquals(QuizMode.TRAINING, configuration.mode()),
                () -> assertEquals(5, configuration.questionCount()),
                () -> assertEquals(4, configuration.optionCount()),
                () -> assertEquals(Set.of(QuizQuestionType.values()), configuration.allowedQuestionTypes()),
                () -> assertTrue(configuration.continentIds().isEmpty()),
                () -> assertTrue(configuration.entityIds().isEmpty()),
                () -> assertTrue(configuration.documentTypes().isEmpty())
        );
    }

    @Test
    void rejectsZeroQuestionCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(0, Set.of(QuizQuestionType.DOCUMENT_TYPE), Optional.empty())
        );
    }

    @Test
    void rejectsEmptyQuestionTypeSelection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(1, Set.of(), Optional.empty())
        );
    }

    @Test
    void preservesTheCanonicalEntityScopeExactly() {
        QuizSessionConfiguration configuration = factory.create(
                3,
                Set.of(QuizQuestionType.DOCUMENT_TYPE),
                Optional.of("entity:ukraine")
        );

        assertAll(
                () -> assertEquals(Set.of("entity:ukraine"), configuration.entityIds()),
                () -> assertTrue(configuration.continentIds().isEmpty()),
                () -> assertTrue(configuration.documentTypes().isEmpty())
        );
    }
}
