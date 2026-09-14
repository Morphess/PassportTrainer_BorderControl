package ua.passporttrainer.model;

import java.time.Instant;
import java.util.List;

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
        questions = List.copyOf(questions);
        answers = List.copyOf(answers);
    }

    public boolean completed() {
        return completedAt != null;
    }
}
