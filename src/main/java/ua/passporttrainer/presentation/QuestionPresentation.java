package ua.passporttrainer.presentation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import ua.passporttrainer.model.QuizQuestionType;

/** JavaFX-independent, answer-safe presentation of one generated question. */
public record QuestionPresentation(
        long questionId,
        QuizQuestionType type,
        String prompt,
        QuestionLayoutKind layout,
        QuestionSubjectPresentation subject,
        List<QuestionOptionPresentation> options
) {

    public QuestionPresentation {
        type = Objects.requireNonNull(type, "type");
        prompt = requireText(prompt, "prompt");
        layout = Objects.requireNonNull(layout, "layout");
        subject = Objects.requireNonNull(subject, "subject");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        if (options.size() < 2) {
            throw new IllegalArgumentException("at least two options are required");
        }
        Set<String> optionIds = new HashSet<>();
        for (QuestionOptionPresentation option : options) {
            if (!optionIds.add(option.optionId())) {
                throw new IllegalArgumentException("option IDs must be unique: " + option.optionId());
            }
        }
        boolean imageSubject = layout == QuestionLayoutKind.IMAGE_SUBJECT_TEXT_OPTIONS;
        if (subject.imageSubject() != imageSubject) {
            throw new IllegalArgumentException("subject does not match question layout");
        }
        boolean imageOptions = layout == QuestionLayoutKind.TEXT_SUBJECT_IMAGE_OPTIONS;
        if (options.stream().anyMatch(option -> option.imageOption() != imageOptions)) {
            throw new IllegalArgumentException("options do not match question layout");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
