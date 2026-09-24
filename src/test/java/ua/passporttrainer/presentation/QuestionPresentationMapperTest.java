package ua.passporttrainer.presentation;

import java.util.List;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.QuizContent;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizQuestionType;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionPresentationMapperTest {

    private static final ResourceBundle RESOURCES = ResourceBundle.getBundle("i18n.messages");
    private final QuestionPresentationMapper mapper = new QuestionPresentationMapper(RESOURCES);

    @Test
    void mapsPassportToCountryAsImageSubjectWithCountryTextOptions() {
        QuizContent.Document subject = document("document:ukraine", "entity:ukraine", DocumentType.ORDINARY_PASSPORT);
        QuizQuestion question = new QuizQuestion(
                1L,
                QuizQuestionType.PASSPORT_TO_COUNTRY,
                subject,
                List.of(
                        new QuizContent.Country("entity:france", "Франція"),
                        new QuizContent.Country("entity:ukraine", "Україна"),
                        new QuizContent.Country("entity:germany", "Німеччина"),
                        new QuizContent.Country("entity:ireland", "Ірландія")
                ),
                "entity:ukraine"
        );

        QuestionPresentation presentation = mapper.map(question);

        assertAll(
                () -> assertEquals(QuestionLayoutKind.IMAGE_SUBJECT_TEXT_OPTIONS, presentation.layout()),
                () -> assertEquals(subject.imageRelativePath(), presentation.subject().imageRelativePath()),
                () -> assertEquals(
                        List.of("entity:france", "entity:ukraine", "entity:germany", "entity:ireland"),
                        presentation.options().stream().map(QuestionOptionPresentation::optionId).toList()
                ),
                () -> assertEquals(
                        List.of("Франція", "Україна", "Німеччина", "Ірландія"),
                        presentation.options().stream().map(QuestionOptionPresentation::label).toList()
                ),
                () -> assertTrue(presentation.options().stream().noneMatch(QuestionOptionPresentation::imageOption))
        );
    }

    @Test
    void mapsCountryToPassportWithNeutralImageOptionLabels() {
        List<QuizContent> options = List.of(
                document("document:france", "entity:france", DocumentType.ORDINARY_PASSPORT),
                document("document:ukraine", "entity:ukraine", DocumentType.ORDINARY_PASSPORT),
                document("document:germany", "entity:germany", DocumentType.ORDINARY_PASSPORT),
                document("document:ireland", "entity:ireland", DocumentType.ORDINARY_PASSPORT)
        );
        QuizQuestion question = new QuizQuestion(
                2L,
                QuizQuestionType.COUNTRY_TO_PASSPORT,
                new QuizContent.Country("entity:ukraine", "Україна"),
                options,
                "document:ukraine"
        );

        QuestionPresentation presentation = mapper.map(question);

        assertAll(
                () -> assertEquals(QuestionLayoutKind.TEXT_SUBJECT_IMAGE_OPTIONS, presentation.layout()),
                () -> assertEquals("Україна", presentation.subject().text()),
                () -> assertEquals(
                        options.stream().map(QuizContent::id).toList(),
                        presentation.options().stream().map(QuestionOptionPresentation::optionId).toList()
                ),
                () -> assertEquals(
                        List.of("Варіант 1", "Варіант 2", "Варіант 3", "Варіант 4"),
                        presentation.options().stream().map(QuestionOptionPresentation::label).toList()
                ),
                () -> assertTrue(presentation.options().stream().allMatch(QuestionOptionPresentation::imageOption)),
                () -> assertTrue(presentation.options().stream()
                        .map(QuestionOptionPresentation::label)
                        .noneMatch(label -> label.contains("Україна") || label.contains("Document")))
        );
    }

    @Test
    void mapsDocumentTypeAsImageSubjectWithLocalizedTypeOptions() {
        QuizContent.Document subject = document(
                "document:service", "entity:ukraine", DocumentType.SERVICE_PASSPORT
        );
        QuizQuestion question = new QuizQuestion(
                3L,
                QuizQuestionType.DOCUMENT_TYPE,
                subject,
                List.of(
                        new QuizContent.DocumentTypeValue(DocumentType.NATIONAL_ID),
                        new QuizContent.DocumentTypeValue(DocumentType.SERVICE_PASSPORT),
                        new QuizContent.DocumentTypeValue(DocumentType.DIPLOMATIC_PASSPORT),
                        new QuizContent.DocumentTypeValue(DocumentType.ORDINARY_PASSPORT)
                ),
                DocumentType.SERVICE_PASSPORT.name()
        );

        QuestionPresentation presentation = mapper.map(question);

        assertAll(
                () -> assertEquals(QuestionLayoutKind.IMAGE_SUBJECT_TEXT_OPTIONS, presentation.layout()),
                () -> assertEquals(subject.imageRelativePath(), presentation.subject().imageRelativePath()),
                () -> assertEquals(
                        List.of(
                                "Національний документ, що посвідчує особу",
                                "Службовий паспорт",
                                "Дипломатичний паспорт",
                                "Звичайний паспорт"
                        ),
                        presentation.options().stream().map(QuestionOptionPresentation::label).toList()
                ),
                () -> assertFalse(presentation.options().stream()
                        .map(QuestionOptionPresentation::label)
                        .anyMatch(label -> label.contains("_")))
        );
    }

    @Test
    void mapsFindOrdinaryPassportWithInstructionAndNeutralImageLabels() {
        List<QuizContent> options = List.of(
                document("document:national", "entity:ireland", DocumentType.NATIONAL_ID),
                document("document:ordinary", "entity:ukraine", DocumentType.ORDINARY_PASSPORT),
                document("document:service", "entity:germany", DocumentType.SERVICE_PASSPORT),
                document("document:diplomatic", "entity:france", DocumentType.DIPLOMATIC_PASSPORT)
        );
        QuizQuestion question = new QuizQuestion(
                4L,
                QuizQuestionType.FIND_ORDINARY_PASSPORT,
                new QuizContent.DocumentTypeValue(DocumentType.ORDINARY_PASSPORT),
                options,
                "document:ordinary"
        );

        QuestionPresentation presentation = mapper.map(question);

        assertAll(
                () -> assertEquals(QuestionLayoutKind.TEXT_SUBJECT_IMAGE_OPTIONS, presentation.layout()),
                () -> assertEquals("Оберіть звичайний паспорт", presentation.subject().text()),
                () -> assertEquals(
                        options.stream().map(QuizContent::id).toList(),
                        presentation.options().stream().map(QuestionOptionPresentation::optionId).toList()
                ),
                () -> assertEquals(
                        List.of("Варіант 1", "Варіант 2", "Варіант 3", "Варіант 4"),
                        presentation.options().stream().map(QuestionOptionPresentation::label).toList()
                ),
                () -> assertTrue(presentation.options().stream().allMatch(QuestionOptionPresentation::imageOption))
        );
    }

    private static QuizContent.Document document(
            String documentId,
            String entityId,
            DocumentType documentType
    ) {
        return new QuizContent.Document(
                documentId,
                entityId,
                documentType,
                "Document title for " + entityId,
                "image:" + documentId,
                "fixtures/" + documentId.replace(':', '-') + ".png"
        );
    }
}
