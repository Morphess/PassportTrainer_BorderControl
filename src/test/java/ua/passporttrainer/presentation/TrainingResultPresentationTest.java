package ua.passporttrainer.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainingResultPresentationTest {

    @Test
    void derivesCorrectIncorrectTotalAndRoundedPercentage() {
        TrainingResultPresentation result = TrainingResultPresentation.fromCounts(2, 3);

        assertAll(
                () -> assertEquals(2, result.correctCount()),
                () -> assertEquals(1, result.incorrectCount()),
                () -> assertEquals(3, result.totalCount()),
                () -> assertEquals(67, result.percentage())
        );
    }

    @Test
    void rejectsCountsOutsideTheCompletedResult() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TrainingResultPresentation.fromCounts(4, 3)
        );
    }
}
