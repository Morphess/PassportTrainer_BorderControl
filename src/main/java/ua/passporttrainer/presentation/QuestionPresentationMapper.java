package ua.passporttrainer.presentation;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import ua.passporttrainer.model.QuizContent;
import ua.passporttrainer.model.QuizQuestion;

/** Maps validated quiz content to localized presentation without disclosing correctness. */
public final class QuestionPresentationMapper {

    private final ResourceBundle resources;

    public QuestionPresentationMapper(ResourceBundle resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public QuestionPresentation map(QuizQuestion question) {
        Objects.requireNonNull(question, "question");
        return switch (question.type()) {
            case PASSPORT_TO_COUNTRY -> imageSubjectWithTextOptions(
                    question,
                    "question.prompt.passportToCountry"
            );
            case COUNTRY_TO_PASSPORT -> textSubjectWithImageOptions(
                    question,
                    ((QuizContent.Country) question.subject()).displayName(),
                    "question.prompt.countryToPassport"
            );
            case DOCUMENT_TYPE -> documentTypeQuestion(question);
            case FIND_ORDINARY_PASSPORT -> textSubjectWithImageOptions(
                    question,
                    resources.getString("question.instruction.findOrdinaryPassport"),
                    "question.prompt.findOrdinaryPassport"
            );
        };
    }

    private QuestionPresentation imageSubjectWithTextOptions(
            QuizQuestion question,
            String promptKey
    ) {
        QuizContent.Document subject = (QuizContent.Document) question.subject();
        List<QuestionOptionPresentation> options = question.options().stream()
                .map(QuizContent.Country.class::cast)
                .map(country -> QuestionOptionPresentation.text(country.id(), country.displayName()))
                .toList();
        return new QuestionPresentation(
                question.id(),
                question.type(),
                resources.getString(promptKey),
                QuestionLayoutKind.IMAGE_SUBJECT_TEXT_OPTIONS,
                QuestionSubjectPresentation.image(subject.imageRelativePath()),
                options
        );
    }

    private QuestionPresentation documentTypeQuestion(QuizQuestion question) {
        QuizContent.Document subject = (QuizContent.Document) question.subject();
        List<QuestionOptionPresentation> options = question.options().stream()
                .map(QuizContent.DocumentTypeValue.class::cast)
                .map(value -> QuestionOptionPresentation.text(
                        value.id(),
                        resources.getString("documentType." + value.documentType().name())
                ))
                .toList();
        return new QuestionPresentation(
                question.id(),
                question.type(),
                resources.getString("question.prompt.documentType"),
                QuestionLayoutKind.IMAGE_SUBJECT_TEXT_OPTIONS,
                QuestionSubjectPresentation.image(subject.imageRelativePath()),
                options
        );
    }

    private QuestionPresentation textSubjectWithImageOptions(
            QuizQuestion question,
            String subjectText,
            String promptKey
    ) {
        List<QuestionOptionPresentation> options = new ArrayList<>(question.options().size());
        for (int index = 0; index < question.options().size(); index++) {
            QuizContent.Document document = (QuizContent.Document) question.options().get(index);
            options.add(QuestionOptionPresentation.image(
                    document.id(),
                    MessageFormat.format(resources.getString("question.imageOptionAccessible"), index + 1),
                    document.imageRelativePath()
            ));
        }
        return new QuestionPresentation(
                question.id(),
                question.type(),
                resources.getString(promptKey),
                QuestionLayoutKind.TEXT_SUBJECT_IMAGE_OPTIONS,
                QuestionSubjectPresentation.text(subjectText),
                options
        );
    }
}
