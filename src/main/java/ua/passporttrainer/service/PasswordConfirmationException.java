package ua.passporttrainer.service;

/** Raised when account-creation password fields do not match. */
public final class PasswordConfirmationException extends IllegalArgumentException {

    public PasswordConfirmationException() {
        super("Password confirmation does not match");
    }
}
