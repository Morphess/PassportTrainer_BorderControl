package ua.passporttrainer.presentation;

import java.util.Objects;

/** One answer option with an internal stable identity and safe visible label. */
public record QuestionOptionPresentation(
        String optionId,
        String label,
        String imageRelativePath
) {

    public QuestionOptionPresentation {
        optionId = requireText(optionId, "optionId");
        label = requireText(label, "label");
        imageRelativePath = imageRelativePath == null ? "" : imageRelativePath.strip();
    }

    public static QuestionOptionPresentation text(String optionId, String label) {
        return new QuestionOptionPresentation(optionId, label, "");
    }

    public static QuestionOptionPresentation image(
            String optionId,
            String neutralLabel,
            String imageRelativePath
    ) {
        return new QuestionOptionPresentation(
                optionId,
                neutralLabel,
                requireText(imageRelativePath, "imageRelativePath")
        );
    }

    public boolean imageOption() {
        return !imageRelativePath.isBlank();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
