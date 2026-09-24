package ua.passporttrainer.presentation;

import java.util.Objects;

/** JavaFX-independent subject shown for one Training question. */
public record QuestionSubjectPresentation(
        String text,
        String imageRelativePath
) {

    public QuestionSubjectPresentation {
        text = normalize(text);
        imageRelativePath = normalize(imageRelativePath);
        if (text.isBlank() == imageRelativePath.isBlank()) {
            throw new IllegalArgumentException("exactly one subject representation is required");
        }
    }

    public static QuestionSubjectPresentation text(String text) {
        return new QuestionSubjectPresentation(requireText(text, "text"), "");
    }

    public static QuestionSubjectPresentation image(String imageRelativePath) {
        return new QuestionSubjectPresentation("", requireText(imageRelativePath, "imageRelativePath"));
    }

    public boolean imageSubject() {
        return !imageRelativePath.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
