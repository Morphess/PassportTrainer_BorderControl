package ua.passporttrainer.service;

import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.security.AuthenticatedUser;
import ua.passporttrainer.security.CurrentUserSession;

/** Resolves personal result history only for the current persisted-active account. */
public final class CurrentUserResultsService {

    private final CurrentUserSession currentUserSession;
    private final AuthorizationService authorizationService;
    private final StatisticsService statisticsService;

    public CurrentUserResultsService(
            CurrentUserSession currentUserSession,
            AuthorizationService authorizationService,
            StatisticsService statisticsService
    ) {
        this.currentUserSession = Objects.requireNonNull(
                currentUserSession, "currentUserSession"
        );
        this.authorizationService = Objects.requireNonNull(
                authorizationService, "authorizationService"
        );
        this.statisticsService = Objects.requireNonNull(
                statisticsService, "statisticsService"
        );
    }

    public Optional<UserResultsSnapshot> findCurrentUserResults() {
        if (currentUserSession.current().isEmpty()) {
            return Optional.empty();
        }
        AuthenticatedUser activeUser = authorizationService.requireActiveUser();
        return Optional.of(statisticsService.findUserResults(activeUser.id()));
    }
}
