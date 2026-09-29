package ua.passporttrainer.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import ua.passporttrainer.repository.AssessmentRepository;
import ua.passporttrainer.repository.MediaRepository;
import ua.passporttrainer.repository.QuestionRepository;
import ua.passporttrainer.repository.QuestionVersionRepository;
import ua.passporttrainer.repository.ResultRepository;
import ua.passporttrainer.repository.UserRepository;
import ua.passporttrainer.repository.sqlite.SQLiteAssessmentRepository;
import ua.passporttrainer.repository.sqlite.SQLiteMediaRepository;
import ua.passporttrainer.repository.sqlite.SQLiteQuestionRepository;
import ua.passporttrainer.repository.sqlite.SQLiteQuestionVersionRepository;
import ua.passporttrainer.repository.sqlite.SQLiteResultRepository;
import ua.passporttrainer.repository.sqlite.SQLiteUserRepository;
import ua.passporttrainer.security.Argon2PasswordHasher;
import ua.passporttrainer.security.CurrentUserSession;
import ua.passporttrainer.security.PasswordHasher;
import ua.passporttrainer.service.AccountSessionService;
import ua.passporttrainer.service.AdminAssessmentService;
import ua.passporttrainer.service.AdminMediaService;
import ua.passporttrainer.service.AdminQuestionService;
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
    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository questionVersionRepository;
    private final MediaRepository mediaRepository;
    private final AssessmentRepository assessmentRepository;
    private final ExamResultService examResultService;
    private final StatisticsService statisticsService;
    private final UserAccountService userAccountService;
    private final AuthenticationService authenticationService;
    private final CurrentUserSession currentUserSession;
    private final AccountSessionService accountSessionService;
    private final AuthorizationService authorizationService;
    private final CurrentUserResultsService currentUserResultsService;
    private final AdminQuestionService adminQuestionService;
    private final AdminAssessmentService adminAssessmentService;
    private final AdminMediaService adminMediaService;

    private ApplicationDatabaseContext(
            ResultRepository resultRepository,
            UserRepository userRepository,
            QuestionRepository questionRepository,
            QuestionVersionRepository questionVersionRepository,
            MediaRepository mediaRepository,
            AssessmentRepository assessmentRepository,
            PasswordHasher passwordHasher,
            Clock clock,
            Supplier<String> idSupplier
    ) {
        this.resultRepository = Objects.requireNonNull(
                resultRepository, "resultRepository"
        );
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.questionRepository = Objects.requireNonNull(
                questionRepository, "questionRepository"
        );
        this.questionVersionRepository = Objects.requireNonNull(
                questionVersionRepository, "questionVersionRepository"
        );
        this.mediaRepository = Objects.requireNonNull(mediaRepository, "mediaRepository");
        this.assessmentRepository = Objects.requireNonNull(
                assessmentRepository, "assessmentRepository"
        );
        Objects.requireNonNull(passwordHasher, "passwordHasher");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(idSupplier, "idSupplier");
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
        adminQuestionService = new AdminQuestionService(
                authorizationService, questionRepository, questionVersionRepository,
                clock, idSupplier
        );
        adminAssessmentService = new AdminAssessmentService(
                authorizationService, assessmentRepository, clock, idSupplier
        );
        adminMediaService = new AdminMediaService(
                authorizationService, mediaRepository, clock, idSupplier
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
                new SQLiteQuestionRepository(connectionProvider),
                new SQLiteQuestionVersionRepository(connectionProvider),
                new SQLiteMediaRepository(connectionProvider),
                new SQLiteAssessmentRepository(connectionProvider),
                new Argon2PasswordHasher(),
                Clock.systemUTC(),
                () -> UUID.randomUUID().toString()
        );
    }

    public ResultRepository resultRepository() {
        return resultRepository;
    }

    public UserRepository userRepository() {
        return userRepository;
    }

    public QuestionRepository questionRepository() {
        return questionRepository;
    }

    public QuestionVersionRepository questionVersionRepository() {
        return questionVersionRepository;
    }

    public MediaRepository mediaRepository() {
        return mediaRepository;
    }

    public AssessmentRepository assessmentRepository() {
        return assessmentRepository;
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

    public AdminQuestionService adminQuestionService() {
        return adminQuestionService;
    }

    public AdminAssessmentService adminAssessmentService() {
        return adminAssessmentService;
    }

    public AdminMediaService adminMediaService() {
        return adminMediaService;
    }

}
