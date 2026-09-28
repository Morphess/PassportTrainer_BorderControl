package ua.passporttrainer.service;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.NewUser;
import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.repository.UserRepository;
import ua.passporttrainer.security.AuthenticatedUser;
import ua.passporttrainer.security.CurrentUserSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    @Test
    void sessionLogsInLogsOutAndProvidesAnonymousFallback() {
        CurrentUserSession session = new CurrentUserSession();
        AuthenticatedUser principal = principal(7, UserRole.USER);

        assertTrue(session.current().isEmpty());
        assertEquals(0, session.currentUserIdOr(0));

        session.login(principal);
        assertEquals(Optional.of(principal), session.current());
        assertEquals(7, session.currentUserIdOr(0));

        session.logout();
        assertTrue(session.current().isEmpty());
        assertEquals(0, session.currentUserIdOr(0));
    }

    @Test
    void authorizationRequiresCurrentPersistedActiveAdministrator() {
        CurrentUserSession session = new CurrentUserSession();
        MutableUserRepository repository = new MutableUserRepository();
        AuthorizationService authorization = new AuthorizationService(session, repository);

        assertThrows(AuthorizationException.class, authorization::requireAdmin);

        repository.user = user(1, UserRole.USER, true);
        session.login(principal(1, UserRole.USER));
        assertThrows(AuthorizationException.class, authorization::requireAdmin);

        repository.user = user(1, UserRole.ADMIN, false);
        session.login(principal(1, UserRole.ADMIN));
        assertThrows(AuthorizationException.class, authorization::requireAdmin);

        repository.user = user(1, UserRole.ADMIN, true);
        assertEquals(UserRole.ADMIN, authorization.requireAdmin().role());
    }

    private static AuthenticatedUser principal(long id, UserRole role) {
        return new AuthenticatedUser(id, "user" + id, "User " + id, role);
    }

    private static User user(long id, UserRole role, boolean active) {
        return new User(
                id, "user" + id, "User " + id, "encoded-hash", role,
                active, NOW, NOW
        );
    }

    private static final class MutableUserRepository implements UserRepository {

        private User user;

        @Override
        public boolean hasUsers() {
            return user != null;
        }

        @Override
        public Optional<User> findById(long id) {
            return user != null && user.id() == id ? Optional.of(user) : Optional.empty();
        }

        @Override
        public Optional<User> findByUsername(String canonicalUsername) {
            return Optional.empty();
        }

        @Override
        public User create(NewUser newUser) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<User> createIfEmpty(NewUser newUser) {
            throw new UnsupportedOperationException();
        }

        @Override
        public User update(User updatedUser) {
            user = updatedUser;
            return updatedUser;
        }
    }
}
