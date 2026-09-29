package ua.passporttrainer.model;

import java.util.Objects;

/** Immutable option belonging to one authored question version. */
public record QuestionOption(
        String optionId,
        int displayOrder,
        String text,
        boolean correct
) {
    public QuestionOption {
        optionId = requireText(optionId, "optionId");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
        text = requireText(text, "text");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
