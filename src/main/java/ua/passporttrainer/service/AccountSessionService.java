package ua.passporttrainer.service;

import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.security.AuthenticatedUser;
import ua.passporttrainer.security.CurrentUserSession;

/** Coordinates local account access with the application-scoped current-user session. */
public final class AccountSessionService {

    private final UserAccountService userAccountService;
    private final AuthenticationService authenticationService;
    private final CurrentUserSession currentUserSession;

    public AccountSessionService(
            UserAccountService userAccountService,
            AuthenticationService authenticationService,
            CurrentUserSession currentUserSession
    ) {
        this.userAccountService = Objects.requireNonNull(
                userAccountService, "userAccountService"
        );
        this.authenticationService = Objects.requireNonNull(
                authenticationService, "authenticationService"
        );
        this.currentUserSession = Objects.requireNonNull(
                currentUserSession, "currentUserSession"
        );
    }

    public boolean initialAdministratorRequired() {
        return userAccountService.initialAdministratorRequired();
    }

    public AuthenticatedUser createInitialAdministratorAndLogin(
            String username,
            String displayName,
            char[] rawPassword,
            char[] passwordConfirmation
    ) {
        AuthenticatedUser principal = userAccountService.createInitialAdministrator(
                username, displayName, rawPassword, passwordConfirmation
        );
        currentUserSession.login(principal);
        return principal;
    }

    public AuthenticatedUser createOrdinaryAccountAndLogin(
            String username,
            String displayName,
            char[] rawPassword,
            char[] passwordConfirmation
    ) {
        AuthenticatedUser principal = userAccountService.createOrdinaryAccount(
                username, displayName, rawPassword, passwordConfirmation
        );
        currentUserSession.login(principal);
        return principal;
    }

    public boolean login(String username, char[] rawPassword) {
        Optional<AuthenticatedUser> authenticated = authenticationService.authenticate(
                username, rawPassword
        );
        authenticated.ifPresent(currentUserSession::login);
        return authenticated.isPresent();
    }

    public void continueAnonymously() {
        currentUserSession.logout();
    }

    public void logout() {
        currentUserSession.logout();
    }

    public void switchUser() {
        currentUserSession.logout();
    }

    public Optional<AuthenticatedUser> currentUser() {
        return currentUserSession.current();
    }
}
