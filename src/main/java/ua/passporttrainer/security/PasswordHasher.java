package ua.passporttrainer.security;

/**
 * Boundary for a future password hashing implementation.
 */
public interface PasswordHasher {

    String hash(char[] rawPassword);

    boolean matches(char[] rawPassword, String passwordHash);
}
