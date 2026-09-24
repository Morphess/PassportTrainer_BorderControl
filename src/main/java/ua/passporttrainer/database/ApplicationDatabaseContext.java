package ua.passporttrainer.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;

import ua.passporttrainer.repository.ResultRepository;
import ua.passporttrainer.repository.sqlite.SQLiteResultRepository;
import ua.passporttrainer.service.ExamResultService;
import ua.passporttrainer.service.StatisticsService;

/** Composition boundary for the separate writable application database. */
public final class ApplicationDatabaseContext {

    private final ResultRepository resultRepository;
    private final ExamResultService examResultService;
    private final StatisticsService statisticsService;

    private ApplicationDatabaseContext(ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
        examResultService = new ExamResultService(resultRepository);
        statisticsService = new StatisticsService(resultRepository);
    }

    public static ApplicationDatabaseContext open(Path databasePath)
            throws IOException, SQLException {
        Path normalizedPath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(normalizedPath.getParent());
        SQLiteConnectionProvider connectionProvider = new SQLiteConnectionProvider(normalizedPath);
        new ApplicationDatabaseMigrationRunner().migrate(connectionProvider);
        return new ApplicationDatabaseContext(new SQLiteResultRepository(connectionProvider));
    }

    public ResultRepository resultRepository() {
        return resultRepository;
    }

    public ExamResultService examResultService() {
        return examResultService;
    }

    public StatisticsService statisticsService() {
        return statisticsService;
    }
}
