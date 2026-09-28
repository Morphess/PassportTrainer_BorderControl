package ua.passporttrainer.service;

/** Raised when ordinary account creation is attempted before initial setup. */
public final class InitialAdministratorRequiredException extends IllegalStateException {

    public InitialAdministratorRequiredException() {
        super("Initial administrator setup is required");
    }
}
