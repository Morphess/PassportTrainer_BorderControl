package ua.passporttrainer.service;

/** Raised when first-run administrator setup has already been completed. */
public final class InitialAdministratorAlreadyExistsException extends IllegalStateException {

    public InitialAdministratorAlreadyExistsException() {
        super("Initial administrator setup has already been completed");
    }
}
