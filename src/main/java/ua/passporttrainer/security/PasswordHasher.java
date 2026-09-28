package ua.passporttrainer.security;

/** Boundary for encoded, salted password hashing implementations. */
public interface PasswordHasher {

    String hash(char[] rawPassword);

    boolean matches(char[] rawPassword, String encodedHash);

    boolean needsRehash(String encodedHash);
}
