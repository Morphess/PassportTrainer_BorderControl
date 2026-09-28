package ua.passporttrainer.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;

import ua.passporttrainer.repository.ResultRepository;
import ua.passporttrainer.repository.UserRepository;
import ua.passporttrainer.repository.sqlite.SQLiteResultRepository;
import ua.passporttrainer.repository.sqlite.SQLiteUserRepository;
import ua.passporttrainer.security.Argon2PasswordHasher;
import ua.passporttrainer.security.CurrentUserSession;
import ua.passporttrainer.security.PasswordHasher;
import ua.passporttrainer.service.AccountSessionService;
import ua.passporttrainer.service.AuthenticationService;
import ua.passporttrainer.service.AuthorizationService;
import ua.passporttrainer.service.CurrentUserResultsService;
import ua.passporttrainer.service.ExamResultService;
import ua.passporttrainer.service.StatisticsService;
import ua.passporttrainer.service.UserAccountService;

/** Composition boundary for the separate writable application database. */
public final class ApplicationDatabaseContext {

    private final ResultRepository resultRepository;
    private final UserRepository userRepository;
    private final ExamResultService examResultService;
    private final StatisticsService statisticsService;
    private final UserAccountService userAccountService;
    private final AuthenticationService authenticationService;
    private final CurrentUserSession currentUserSession;
    private final AccountSessionService accountSessionService;
    private final AuthorizationService authorizationService;
    private final CurrentUserResultsService currentUserResultsService;

    private ApplicationDatabaseContext(
            ResultRepository resultRepository,
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock
    ) {
        this.resultRepository = Objects.requireNonNull(
                resultRepository, "resultRepository"
        );
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        Objects.requireNonNull(passwordHasher, "passwordHasher");
        Objects.requireNonNull(clock, "clock");
        examResultService = new ExamResultService(resultRepository);
        statisticsService = new StatisticsService(resultRepository);
        userAccountService = new UserAccountService(userRepository, passwordHasher, clock);
        authenticationService = new AuthenticationService(userRepository, passwordHasher, clock);
        currentUserSession = new CurrentUserSession();
        accountSessionService = new AccountSessionService(
                userAccountService, authenticationService, currentUserSession
        );
        authorizationService = new AuthorizationService(currentUserSession, userRepository);
        currentUserResultsService = new CurrentUserResultsService(
                currentUserSession, authorizationService, statisticsService
        );
    }

    public static ApplicationDatabaseContext open(Path databasePath)
            throws IOException, SQLException {
        Path normalizedPath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(normalizedPath.getParent());
        SQLiteConnectionProvider connectionProvider = new SQLiteConnectionProvider(normalizedPath);
        new ApplicationDatabaseMigrationRunner().migrate(connectionProvider);
        return new ApplicationDatabaseContext(
                new SQLiteResultRepository(connectionProvider),
                new SQLiteUserRepository(connectionProvider),
                new Argon2PasswordHasher(),
                Clock.systemUTC()
        );
    }

    public ResultRepository resultRepository() {
        return resultRepository;
    }

    public UserRepository userRepository() {
        return userRepository;
    }

    public ExamResultService examResultService() {
        return examResultService;
    }

    public StatisticsService statisticsService() {
        return statisticsService;
    }

    public UserAccountService userAccountService() {
        return userAccountService;
    }

    public AuthenticationService authenticationService() {
        return authenticationService;
    }

    public CurrentUserSession currentUserSession() {
        return currentUserSession;
    }

    public AccountSessionService accountSessionService() {
        return accountSessionService;
    }

    public AuthorizationService authorizationService() {
        return authorizationService;
    }

    public CurrentUserResultsService currentUserResultsService() {
        return currentUserResultsService;
    }
}
