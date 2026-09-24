package ua.passporttrainer.controller;

import ua.passporttrainer.model.QuizMode;

/** User-confirmation boundary for abandoning an active quiz session. */
@FunctionalInterface
public interface SessionAbandonmentConfirmation {

    boolean confirmAbandonment(QuizMode mode);
}
