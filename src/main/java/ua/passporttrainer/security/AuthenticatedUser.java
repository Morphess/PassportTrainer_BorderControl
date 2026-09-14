package ua.passporttrainer.security;

import ua.passporttrainer.model.User;

/**
 * Current authenticated principal exposed to controllers.
 */
public record AuthenticatedUser(User user) {
}
