package ua.passporttrainer.security;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Application-scoped in-memory holder for the current local principal. */
public final class CurrentUserSession {

    private final AtomicReference<AuthenticatedUser> current = new AtomicReference<>();

    public void login(AuthenticatedUser principal) {
        current.set(Objects.requireNonNull(principal, "principal"));
    }

    public void logout() {
        current.set(null);
    }

    public Optional<AuthenticatedUser> current() {
        return Optional.ofNullable(current.get());
    }

    public long currentUserIdOr(long anonymousUserId) {
        AuthenticatedUser principal = current.get();
        return principal == null ? anonymousUserId : principal.id();
    }
}
