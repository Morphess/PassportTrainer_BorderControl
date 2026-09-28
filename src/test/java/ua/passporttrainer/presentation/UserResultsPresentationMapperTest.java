package ua.passporttrainer.presentation;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.AssessmentSource;
import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.service.UserResultStatistics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserResultsPresentationMapperTest {

    private static final ResourceBundle RESOURCES = ResourceBundle.getBundle("i18n.messages");
    private final UserResultsPresentationMapper mapper =
            new UserResultsPresentationMapper(RESOURCES, ZoneOffset.UTC);

    @Test
    void mapsVisibleResultFactsWithoutTechnicalIdentifiers() {
        ExamResult result = new ExamResult(
                91,
                7L,
                28,
                AssessmentSource.GENERATED,
                Optional.empty(),
                Instant.parse("2026-09-24T12:01:30Z"),
                Instant.parse("2026-09-24T12:03:00Z"),
                2,
                3
        );

        ResultHistoryItemPresentation presentation = mapper.mapResult(result);

        assertAll(
                () -> assertEquals("24.09.2026 12:03", presentation.completedAt()),
                () -> assertEquals("Екзамен · згенерований", presentation.assessment()),
                () -> assertEquals("3", presentation.totalQuestions()),
                () -> assertEquals("2", presentation.correctAnswers()),
                () -> assertEquals("66,7%", presentation.percentage()),
                () -> assertEquals("90 с", presentation.duration())
        );
    }

    @Test
    void mapsDerivedStatisticsAndUsesUnavailableMarkerForEmptyPercentages() {
        UserStatisticsPresentation populated = mapper.mapStatistics(new UserResultStatistics(
                2,
                OptionalDouble.of(62.5),
                OptionalDouble.of(75.0),
                6,
                4
        ));
        UserStatisticsPresentation empty = mapper.mapStatistics(new UserResultStatistics(
                0,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                0,
                0
        ));

        assertAll(
                () -> assertEquals("2", populated.completedExaminations()),
                () -> assertEquals("62,5%", populated.averagePercentage()),
                () -> assertEquals("75%", populated.bestPercentage()),
                () -> assertEquals("6", populated.totalAnswered()),
                () -> assertEquals("4", populated.totalCorrect()),
                () -> assertEquals("—", empty.averagePercentage()),
                () -> assertEquals("—", empty.bestPercentage())
        );
    }
}
