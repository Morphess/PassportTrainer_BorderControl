package ua.passporttrainer.controller;

/** User-confirmation boundary for abandoning an active Training session. */
@FunctionalInterface
public interface TrainingAbandonmentConfirmation {

    boolean confirmAbandonment();
}
