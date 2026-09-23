package ua.passporttrainer.service;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.presentation.LearningEntityPresentation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LearningServiceProductionTest {

    private static final Path PRODUCTION_CATALOGUE =
            Path.of("data", "passport-catalogue", "passport-catalogue.sqlite");

    @Test
    void checkedInCatalogueExposesTheReviewedLearningPoolReadOnly() throws Exception {
        LearningService learningService = CatalogueApplicationContext
                .openReadOnly(PRODUCTION_CATALOGUE)
                .learningService();
        Map<String, LearningEntityPresentation> entities = learningService.findEligibleEntities().stream()
                .collect(Collectors.toUnmodifiableMap(
                        LearningEntityPresentation::displayName,
                        Function.identity()
                ));

        int documentCount = entities.values().stream()
                .mapToInt(LearningEntityPresentation::documentCount)
                .sum();

        assertAll(
                () -> assertEquals(4, entities.size()),
                () -> assertEquals(12, documentCount),
                () -> assertEquals(4, entities.get("Україна").documentCount()),
                () -> assertEquals(3, entities.get("Франція").documentCount()),
                () -> assertEquals(3, entities.get("Німеччина").documentCount()),
                () -> assertEquals(2, entities.get("Ірландія").documentCount())
        );
    }
}
