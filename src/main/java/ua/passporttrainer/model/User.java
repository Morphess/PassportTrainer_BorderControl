package ua.passporttrainer.model;

import java.time.Instant;
import java.util.Objects;

/** Persisted local account; the password field contains only an encoded hash. */
public record User(
        long id,
        String username,
        String displayName,
        String passwordHash,
        UserRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public User {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        username = requireText(username, "username");
        displayName = requireText(displayName, "displayName");
        passwordHash = requireText(passwordHash, "passwordHash");
        role = Objects.requireNonNull(role, "role");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
