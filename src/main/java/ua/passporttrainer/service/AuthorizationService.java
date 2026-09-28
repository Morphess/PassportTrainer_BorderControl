package ua.passporttrainer.service;

import java.util.Objects;

import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.repository.UserRepository;
import ua.passporttrainer.security.AuthenticatedUser;
import ua.passporttrainer.security.CurrentUserSession;

/** Enforces application-service authorization against current persisted account state. */
public final class AuthorizationService {

    private final CurrentUserSession currentUserSession;
    private final UserRepository userRepository;

    public AuthorizationService(
            CurrentUserSession currentUserSession,
            UserRepository userRepository
    ) {
        this.currentUserSession = Objects.requireNonNull(
                currentUserSession, "currentUserSession"
        );
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    public AuthenticatedUser requireAdmin() {
        AuthenticatedUser principal = currentUserSession.current()
                .orElseThrow(AuthorizationService::adminRequired);
        User user = userRepository.findById(principal.id())
                .orElseThrow(AuthorizationService::adminRequired);
        if (!user.active() || user.role() != UserRole.ADMIN) {
            throw adminRequired();
        }
        return AuthenticatedUser.from(user);
    }

    private static AuthorizationException adminRequired() {
        return new AuthorizationException("Active administrator authentication is required");
    }
}
