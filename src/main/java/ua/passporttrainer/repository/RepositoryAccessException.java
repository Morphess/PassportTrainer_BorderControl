package ua.passporttrainer.repository;

/** Indicates that a repository could not access or map its persistence state. */
public final class RepositoryAccessException extends RuntimeException {

    public RepositoryAccessException(String message) {
        super(message);
    }

    public RepositoryAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
