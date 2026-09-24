package ua.passporttrainer.service;

import java.util.Set;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSessionConfiguration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExaminationConfigurationFactoryTest {

    @Test
    void createsFixedGlobalTenQuestionFourOptionPolicy() {
        QuizSessionConfiguration configuration = new ExaminationConfigurationFactory().create();

        assertAll(
                () -> assertEquals(QuizMode.EXAMINATION, configuration.mode()),
                () -> assertEquals(10, configuration.questionCount()),
                () -> assertEquals(4, configuration.optionCount()),
                () -> assertEquals(Set.of(QuizQuestionType.values()), configuration.allowedQuestionTypes()),
                () -> assertTrue(configuration.continentIds().isEmpty()),
                () -> assertTrue(configuration.entityIds().isEmpty()),
                () -> assertTrue(configuration.documentTypes().isEmpty())
        );
    }
}
