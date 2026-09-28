package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.NewUser;
import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.repository.UserRepository;
import ua.passporttrainer.security.AuthenticatedUser;
import ua.passporttrainer.security.PasswordHasher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceTest {

    private static final Instant CREATED = Instant.parse("2026-09-24T10:00:00Z");
    private static final Instant AUTHENTICATED = Instant.parse("2026-09-24T12:00:00Z");

    private InMemoryUserRepository repository;
    private TrackingPasswordHasher passwordHasher;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        passwordHasher = new TrackingPasswordHasher();
        authenticationService = new AuthenticationService(
                repository,
                passwordHasher,
                Clock.fixed(AUTHENTICATED, ZoneOffset.UTC)
        );
    }

    @Test
    void canonicalizesLoginReturnsPasswordFreePrincipalAndRehashesOnSuccess() {
        repository.store(user(1, "alice", "old-hash", true));

        AuthenticatedUser principal = authenticationService.authenticate(
                "  ＡLICE ", "correct password".toCharArray()
        ).orElseThrow();
        User persisted = repository.findById(1).orElseThrow();

        assertEquals(1, principal.id());
        assertEquals("alice", principal.username());
        assertEquals("Alice", principal.displayName());
        assertEquals(UserRole.USER, principal.role());
        assertEquals("current-hash", persisted.passwordHash());
        assertEquals(AUTHENTICATED, persisted.updatedAt());
        assertEquals(1, passwordHasher.hashCalls);
    }

    @Test
    void unknownWrongAndInactiveCredentialsHaveTheSameEmptyResult() {
        repository.store(user(1, "alice", "current-hash", true));
        repository.store(user(2, "inactive", "current-hash", false));

        Optional<AuthenticatedUser> unknown = authenticationService.authenticate(
                "unknown", "correct password".toCharArray()
        );
        Optional<AuthenticatedUser> wrong = authenticationService.authenticate(
                "alice", "wrong password".toCharArray()
        );
        Optional<AuthenticatedUser> inactive = authenticationService.authenticate(
                "inactive", "correct password".toCharArray()
        );
        Optional<AuthenticatedUser> invalid = authenticationService.authenticate(
                "   ", "correct password".toCharArray()
        );

        assertTrue(unknown.isEmpty());
        assertTrue(wrong.isEmpty());
        assertTrue(inactive.isEmpty());
        assertTrue(invalid.isEmpty());
        assertFalse(repository.findById(2).orElseThrow().active());
        assertEquals(0, passwordHasher.hashCalls);
    }

    private static User user(long id, String username, String hash, boolean active) {
        return new User(
                id, username, username.equals("alice") ? "Alice" : "Inactive", hash,
                UserRole.USER, active, CREATED, CREATED
        );
    }

    private static final class TrackingPasswordHasher implements PasswordHasher {

        private int hashCalls;

        @Override
        public String hash(char[] rawPassword) {
            hashCalls++;
            return "current-hash";
        }

        @Override
        public boolean matches(char[] rawPassword, String encodedHash) {
            return String.valueOf(rawPassword).equals("correct password")
                    && (encodedHash.equals("old-hash") || encodedHash.equals("current-hash"));
        }

        @Override
        public boolean needsRehash(String encodedHash) {
            return encodedHash.equals("old-hash");
        }
    }

    private static final class InMemoryUserRepository implements UserRepository {

        private final Map<Long, User> users = new HashMap<>();

        private void store(User user) {
            users.put(user.id(), user);
        }

        @Override
        public boolean hasUsers() {
            return !users.isEmpty();
        }

        @Override
        public Optional<User> findById(long id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<User> findByUsername(String canonicalUsername) {
            return users.values().stream()
                    .filter(user -> user.username().equals(canonicalUsername))
                    .findFirst();
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
        public User update(User user) {
            users.put(user.id(), user);
            return user;
        }
    }
}
