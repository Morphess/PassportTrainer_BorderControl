package ua.passporttrainer.model;

import java.time.Instant;
import java.util.Objects;

/** Validated account data awaiting a database-generated user ID. */
public record NewUser(
        String username,
        String displayName,
        String passwordHash,
        UserRole role,
        Instant createdAt
) {
    public NewUser {
        username = requireText(username, "username");
        displayName = requireText(displayName, "displayName");
        passwordHash = requireText(passwordHash, "passwordHash");
        role = Objects.requireNonNull(role, "role");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
