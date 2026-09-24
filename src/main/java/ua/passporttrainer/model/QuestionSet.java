package ua.passporttrainer.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Ordered questions supplied by one source, with optional immutable assessment provenance. */
public record QuestionSet(
        List<QuizQuestion> questions,
        Optional<AssessmentProvenance> assessmentProvenance
) {

    public QuestionSet {
        questions = List.copyOf(Objects.requireNonNull(questions, "questions"));
        assessmentProvenance = Objects.requireNonNull(
                assessmentProvenance, "assessmentProvenance"
        );
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("questions must not be empty");
        }
        Set<Long> questionIds = new HashSet<>();
        for (QuizQuestion question : questions) {
            Objects.requireNonNull(question, "question");
            if (!questionIds.add(question.id())) {
                throw new IllegalArgumentException("Question IDs must be unique");
            }
        }
    }

    public static QuestionSet generated(List<QuizQuestion> questions) {
        return new QuestionSet(questions, Optional.empty());
    }
}
