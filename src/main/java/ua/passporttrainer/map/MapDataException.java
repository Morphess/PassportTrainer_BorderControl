package ua.passporttrainer.map;

/** Signals missing, malformed, inconsistent, or tampered map resources. */
public final class MapDataException extends RuntimeException {

    public MapDataException(String message) {
        super(message);
    }

    public MapDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
