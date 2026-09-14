package ua.passporttrainer.model;

/**
 * A local application user. Only a password hash may be stored here.
 */
public record User(
        long id,
        String username,
        String passwordHash,
        UserRole role,
        boolean active
) {
}
