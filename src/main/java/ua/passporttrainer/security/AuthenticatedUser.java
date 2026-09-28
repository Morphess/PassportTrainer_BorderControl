package ua.passporttrainer.security;

import java.util.Objects;

import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;

/** Password-free identity safe to expose to controllers. */
public record AuthenticatedUser(
        long id,
        String username,
        String displayName,
        UserRole role
) {
    public AuthenticatedUser {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        username = Objects.requireNonNull(username, "username");
        displayName = Objects.requireNonNull(displayName, "displayName");
        role = Objects.requireNonNull(role, "role");
    }

    public static AuthenticatedUser from(User user) {
        Objects.requireNonNull(user, "user");
        return new AuthenticatedUser(
                user.id(), user.username(), user.displayName(), user.role()
        );
    }
}
