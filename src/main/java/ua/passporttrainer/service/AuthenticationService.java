package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.model.User;
import ua.passporttrainer.repository.UserRepository;
import ua.passporttrainer.security.AuthenticatedUser;
import ua.passporttrainer.security.PasswordHasher;

/** Authenticates active local accounts without exposing failure details. */
public final class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<AuthenticatedUser> authenticate(String username, char[] rawPassword) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(rawPassword, "rawPassword");

        String canonicalUsername;
        try {
            canonicalUsername = UsernameNormalizer.normalize(username);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        char[] passwordCopy = rawPassword.clone();
        try {
            Optional<User> candidate = userRepository.findByUsername(canonicalUsername);
            if (candidate.isEmpty()) {
                return Optional.empty();
            }
            User user = candidate.orElseThrow();
            if (!user.active()
                    || !passwordHasher.matches(passwordCopy, user.passwordHash())) {
                return Optional.empty();
            }
            if (passwordHasher.needsRehash(user.passwordHash())) {
                user = userRepository.update(withPasswordHash(
                        user,
                        passwordHasher.hash(passwordCopy),
                        monotonicNow(user.updatedAt())
                ));
            }
            return Optional.of(AuthenticatedUser.from(user));
        } finally {
            Arrays.fill(passwordCopy, '\0');
        }
    }

    private Instant monotonicNow(Instant previous) {
        Instant now = clock.instant();
        return now.isBefore(previous) ? previous : now;
    }

    private static User withPasswordHash(User user, String passwordHash, Instant updatedAt) {
        return new User(
                user.id(),
                user.username(),
                user.displayName(),
                passwordHash,
                user.role(),
                user.active(),
                user.createdAt(),
                updatedAt
        );
    }
}
