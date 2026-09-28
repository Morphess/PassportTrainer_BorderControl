package ua.passporttrainer.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.database.ApplicationDatabaseContext;
import ua.passporttrainer.model.AssessmentSource;
import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.security.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserResultsServiceTest {

    private static final Instant STARTED = Instant.parse("2026-09-24T12:00:00Z");
    private static final char[] PASSWORD = "correct password".toCharArray();

    @TempDir
    Path temporaryDirectory;

    @Test
    void returnsOnlyCurrentActiveUsersHistoryAndDerivesStatistics() throws Exception {
        ApplicationDatabaseContext context = open("owned-results.sqlite");
        createAdministrator(context);
        AuthenticatedUser first = createUser(context, "first");
        AuthenticatedUser second = createUser(context, "second");
        ExamResult older = result(10, first.id(), 100, 3, 4, 90);
        ExamResult newer = result(11, first.id(), 101, 1, 2, 120);
        ExamResult otherOwner = result(12, second.id(), 102, 5, 5, 150);
        context.resultRepository().save(older);
        context.resultRepository().save(newer);
        context.resultRepository().save(otherOwner);

        context.currentUserSession().login(first);
        UserResultsSnapshot firstSnapshot = context.currentUserResultsService()
                .findCurrentUserResults().orElseThrow();
        UserResultStatistics statistics = firstSnapshot.statistics();

        assertAll(
                () -> assertEquals(2, firstSnapshot.results().size()),
                () -> assertEquals(newer, firstSnapshot.results().getFirst()),
                () -> assertEquals(older, firstSnapshot.results().getLast()),
                () -> assertTrue(firstSnapshot.results().stream()
                        .allMatch(result -> result.userId().equals(first.id()))),
                () -> assertEquals(2, statistics.completedExaminations()),
                () -> assertEquals(
                        62.5, statistics.averagePercentage().orElseThrow(), 0.0001
                ),
                () -> assertEquals(
                        75.0, statistics.bestPercentage().orElseThrow(), 0.0001
                ),
                () -> assertEquals(6, statistics.totalAnswered()),
                () -> assertEquals(4, statistics.totalCorrect())
        );

        context.currentUserSession().login(second);
        UserResultsSnapshot secondSnapshot = context.currentUserResultsService()
                .findCurrentUserResults().orElseThrow();
        assertAll(
                () -> assertEquals(1, secondSnapshot.results().size()),
                () -> assertEquals(otherOwner, secondSnapshot.results().getFirst()),
                () -> assertEquals(100.0,
                        secondSnapshot.statistics().bestPercentage().orElseThrow())
        );
    }

    @Test
    void anonymousAccessSkipsResultQueryAndAuthenticatedEmptyHistoryHasEmptyStatistics()
            throws Exception {
        ApplicationDatabaseContext context = open("empty-results.sqlite");
        createAdministrator(context);
        AuthenticatedUser user = createUser(context, "empty");

        assertTrue(context.currentUserResultsService().findCurrentUserResults().isEmpty());

        context.currentUserSession().login(user);
        UserResultsSnapshot snapshot = context.currentUserResultsService()
                .findCurrentUserResults().orElseThrow();
        assertAll(
                () -> assertTrue(snapshot.results().isEmpty()),
                () -> assertEquals(0, snapshot.statistics().completedExaminations()),
                () -> assertTrue(snapshot.statistics().averagePercentage().isEmpty()),
                () -> assertTrue(snapshot.statistics().bestPercentage().isEmpty()),
                () -> assertEquals(0, snapshot.statistics().totalAnswered()),
                () -> assertEquals(0, snapshot.statistics().totalCorrect())
        );
    }

    @Test
    void inactiveCurrentAccountCannotReadPersonalStatistics() throws Exception {
        ApplicationDatabaseContext context = open("inactive-results.sqlite");
        createAdministrator(context);
        AuthenticatedUser user = createUser(context, "inactive");
        context.currentUserSession().login(user);
        context.userAccountService().deactivate(user.id());

        assertThrows(
                AuthorizationException.class,
                () -> context.currentUserResultsService().findCurrentUserResults()
        );
    }

    private ApplicationDatabaseContext open(String filename) throws Exception {
        return ApplicationDatabaseContext.open(temporaryDirectory.resolve(filename));
    }

    private static void createAdministrator(ApplicationDatabaseContext context) {
        context.userAccountService().createInitialAdministrator(
                "admin", "Administrator", PASSWORD, PASSWORD
        );
    }

    private static AuthenticatedUser createUser(
            ApplicationDatabaseContext context,
            String username
    ) {
        return context.userAccountService().createOrdinaryAccount(
                username, "User " + username, PASSWORD, PASSWORD
        );
    }

    private static ExamResult result(
            long id,
            long userId,
            long sessionId,
            int correct,
            int total,
            long completionOffsetSeconds
    ) {
        return new ExamResult(
                id,
                userId,
                sessionId,
                AssessmentSource.GENERATED,
                Optional.empty(),
                STARTED,
                STARTED.plusSeconds(completionOffsetSeconds),
                correct,
                total
        );
    }
}
