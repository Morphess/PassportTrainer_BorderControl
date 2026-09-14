package ua.passporttrainer.service;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.model.User;
import ua.passporttrainer.repository.UserRepository;
import ua.passporttrainer.security.AuthenticatedUser;
import ua.passporttrainer.security.PasswordHasher;

public final class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthenticationService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }

    public Optional<AuthenticatedUser> authenticate(String username, char[] rawPassword) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(rawPassword, "rawPassword");

        char[] passwordCopy = rawPassword.clone();
        try {
            return userRepository.findByUsername(username)
                    .filter(User::active)
                    .filter(user -> passwordHasher.matches(passwordCopy, user.passwordHash()))
                    .map(AuthenticatedUser::new);
        } finally {
            Arrays.fill(passwordCopy, '\0');
        }
    }
}
