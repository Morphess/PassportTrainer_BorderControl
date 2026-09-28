package ua.passporttrainer.service;

/** Indicates that a canonical local username is already registered. */
public final class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException() {
        super("Username is already registered");
    }
}
