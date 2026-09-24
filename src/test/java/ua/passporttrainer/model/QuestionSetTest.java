package ua.passporttrainer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSetTest {

    @Test
    void preservesOrderDefensivelyAndCarriesOptionalProvenance() {
        List<QuizQuestion> source = new ArrayList<>(List.of(question(1), question(2)));
        AssessmentProvenance provenance = new AssessmentProvenance("test:alpha", "version:1");

        QuestionSet questionSet = new QuestionSet(source, Optional.of(provenance));
        source.clear();

        assertEquals(List.of(1L, 2L), questionSet.questions().stream().map(QuizQuestion::id).toList());
        assertEquals(Optional.of(provenance), questionSet.assessmentProvenance());
        assertThrows(UnsupportedOperationException.class, () -> questionSet.questions().clear());
    }

    @Test
    void rejectsEmptyOrDuplicateQuestionIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestionSet.generated(List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestionSet.generated(List.of(question(1), question(1)))
        );
    }

    @Test
    void generatedSetHasNoAuthoredAssessmentProvenance() {
        assertTrue(QuestionSet.generated(List.of(question(1))).assessmentProvenance().isEmpty());
    }

    private static QuizQuestion question(long id) {
        return new QuizQuestion(
                id,
                QuizQuestionType.DOCUMENT_TYPE,
                new QuizContent.Document(
                        "document:" + id,
                        "entity:" + id,
                        DocumentType.ORDINARY_PASSPORT,
                        "Document " + id,
                        "image:" + id,
                        "fixtures/" + id + ".jpg"
                ),
                List.of(
                        new QuizContent.DocumentTypeValue(DocumentType.ORDINARY_PASSPORT),
                        new QuizContent.DocumentTypeValue(DocumentType.DIPLOMATIC_PASSPORT)
                ),
                DocumentType.ORDINARY_PASSPORT.name()
        );
    }
}
