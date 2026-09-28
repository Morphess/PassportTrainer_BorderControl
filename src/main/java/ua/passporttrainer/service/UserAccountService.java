package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import ua.passporttrainer.model.NewUser;
import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.repository.UserRepository;
import ua.passporttrainer.security.PasswordHasher;
import ua.passporttrainer.security.AuthenticatedUser;

/** Owns validated local-account creation and profile changes. */
public final class UserAccountService {

    private static final int MINIMUM_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public UserAccountService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean initialAdministratorRequired() {
        return !userRepository.hasUsers();
    }

    public AuthenticatedUser createInitialAdministrator(
            String username,
            String displayName,
            char[] rawPassword,
            char[] passwordConfirmation
    ) {
        if (userRepository.hasUsers()) {
            throw new InitialAdministratorAlreadyExistsException();
        }
        NewUser newUser = validatedNewUser(
                username, displayName, rawPassword, passwordConfirmation, UserRole.ADMIN
        );
        return userRepository.createIfEmpty(newUser)
                .map(AuthenticatedUser::from)
                .orElseThrow(InitialAdministratorAlreadyExistsException::new);
    }

    public AuthenticatedUser createOrdinaryAccount(
            String username,
            String displayName,
            char[] rawPassword,
            char[] passwordConfirmation
    ) {
        if (!userRepository.hasUsers()) {
            throw new InitialAdministratorRequiredException();
        }
        NewUser newUser = validatedNewUser(
                username, displayName, rawPassword, passwordConfirmation, UserRole.USER
        );
        if (userRepository.findByUsername(newUser.username()).isPresent()) {
            throw new UsernameAlreadyExistsException();
        }
        return AuthenticatedUser.from(userRepository.create(newUser));
    }

    public User updateDisplayName(long userId, String displayName) {
        User user = requireUser(userId);
        return userRepository.update(copy(
                user, requireDisplayName(displayName), user.passwordHash(),
                user.role(), user.active()
        ));
    }

    public User deactivate(long userId) {
        User user = requireUser(userId);
        return user.active()
                ? userRepository.update(copy(
                        user, user.displayName(), user.passwordHash(), user.role(), false
                ))
                : user;
    }

    private NewUser validatedNewUser(
            String username,
            String displayName,
            char[] rawPassword,
            char[] passwordConfirmation,
            UserRole role
    ) {
        String canonicalUsername = UsernameNormalizer.normalize(username);
        String validatedDisplayName = requireDisplayName(displayName);
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(passwordConfirmation, "passwordConfirmation");
        Objects.requireNonNull(role, "role");
        if (!Arrays.equals(rawPassword, passwordConfirmation)) {
            throw new PasswordConfirmationException();
        }
        if (rawPassword.length < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("password must contain at least 8 characters");
        }

        char[] passwordCopy = rawPassword.clone();
        try {
            return new NewUser(
                    canonicalUsername,
                    validatedDisplayName,
                    passwordHasher.hash(passwordCopy),
                    role,
                    clock.instant()
            );
        } finally {
            Arrays.fill(passwordCopy, '\0');
        }
    }

    private User requireUser(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return userRepository.findById(userId).orElseThrow(() ->
                new IllegalArgumentException("Unknown user ID: " + userId)
        );
    }

    private User copy(
            User user,
            String displayName,
            String passwordHash,
            UserRole role,
            boolean active
    ) {
        Instant updatedAt = clock.instant();
        if (updatedAt.isBefore(user.updatedAt())) {
            updatedAt = user.updatedAt();
        }
        return new User(
                user.id(), user.username(), displayName, passwordHash, role, active,
                user.createdAt(), updatedAt
        );
    }

    private static String requireDisplayName(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        String validated = displayName.strip();
        if (validated.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return validated;
    }
}
