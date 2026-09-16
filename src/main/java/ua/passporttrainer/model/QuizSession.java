package ua.passporttrainer.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * State of one learning, training, or examination session.
 */
public record QuizSession(
        long id,
        long userId,
        QuizMode mode,
        Instant startedAt,
        Instant completedAt,
        List<QuizQuestion> questions,
        List<QuizAnswer> answers
) {
    public QuizSession {
        mode = Objects.requireNonNull(mode, "mode");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        questions = List.copyOf(Objects.requireNonNull(questions, "questions"));
        answers = List.copyOf(Objects.requireNonNull(answers, "answers"));

        if (questions.isEmpty()) {
            throw new IllegalArgumentException("questions must not be empty");
        }

        Map<Long, QuizQuestion> questionsById = new HashMap<>();
        for (QuizQuestion question : questions) {
            if (questionsById.putIfAbsent(question.id(), question) != null) {
                throw new IllegalArgumentException("question IDs must be unique: " + question.id());
            }
        }

        Set<Long> answeredQuestionIds = new HashSet<>();
        for (QuizAnswer answer : answers) {
            QuizQuestion question = questionsById.get(answer.questionId());
            if (question == null) {
                throw new IllegalArgumentException("answer references unknown question: " + answer.questionId());
            }
            if (!answeredQuestionIds.add(answer.questionId())) {
                throw new IllegalArgumentException("question already answered: " + answer.questionId());
            }
            if (!question.hasOption(answer.selectedAnswerId())) {
                throw new IllegalArgumentException(
                        "answer does not identify a question option: " + answer.questionId()
                );
            }
            boolean expectedCorrect = question.correctAnswerId().equals(answer.selectedAnswerId());
            if (answer.correct() != expectedCorrect) {
                throw new IllegalArgumentException("answer correctness does not match question: " + answer.questionId());
            }
        }
    }

    public boolean completed() {
        return completedAt != null;
    }
}
