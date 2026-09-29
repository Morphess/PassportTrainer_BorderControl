package ua.passporttrainer.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable authored content version projected to runtime questions only by a future provider. */
public record QuestionVersion(
        String versionId,
        String questionId,
        int versionNumber,
        AuthoredQuestionType questionType,
        String prompt,
        Optional<String> instructions,
        Optional<String> explanation,
        Optional<String> difficulty,
        List<QuestionOption> options,
        long createdBy,
        Instant createdAt
) {
    public QuestionVersion {
        versionId = requireText(versionId, "versionId");
        questionId = requireText(questionId, "questionId");
        if (versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }
        questionType = Objects.requireNonNull(questionType, "questionType");
        prompt = requireText(prompt, "prompt");
        instructions = requireOptionalText(instructions, "instructions");
        explanation = requireOptionalText(explanation, "explanation");
        difficulty = requireOptionalText(difficulty, "difficulty");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        validateOptions(options);
        if (createdBy <= 0) {
            throw new IllegalArgumentException("createdBy must be positive");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static void validateOptions(List<QuestionOption> options) {
        if (options.size() < 2) {
            throw new IllegalArgumentException("options must contain at least two values");
        }
        Set<String> optionIds = new HashSet<>();
        int correctCount = 0;
        for (int index = 0; index < options.size(); index++) {
            QuestionOption option = Objects.requireNonNull(options.get(index), "option");
            if (!optionIds.add(option.optionId())) {
                throw new IllegalArgumentException("option IDs must be unique");
            }
            if (option.displayOrder() != index) {
                throw new IllegalArgumentException(
                        "option displayOrder must be contiguous and match list order"
                );
            }
            if (option.correct()) {
                correctCount++;
            }
        }
        if (correctCount != 1) {
            throw new IllegalArgumentException("options must contain exactly one correct value");
        }
    }

    private static Optional<String> requireOptionalText(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(text -> requireText(text, name));
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
