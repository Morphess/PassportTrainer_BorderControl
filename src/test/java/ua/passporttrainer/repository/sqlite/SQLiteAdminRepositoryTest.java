package ua.passporttrainer.repository.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.database.ApplicationDatabaseContext;
import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.database.SQLiteConnectionProvider;
import ua.passporttrainer.model.AssessmentDefinition;
import ua.passporttrainer.model.AssessmentState;
import ua.passporttrainer.model.AuthoredQuestionType;
import ua.passporttrainer.model.MediaMetadata;
import ua.passporttrainer.model.MediaReferenceType;
import ua.passporttrainer.model.NewUser;
import ua.passporttrainer.model.Question;
import ua.passporttrainer.model.QuestionLifecycleState;
import ua.passporttrainer.model.QuestionOption;
import ua.passporttrainer.model.QuestionVersion;
import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.repository.RepositoryAccessException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteAdminRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsQuestionLifecycleAndListsDeterministically() throws Exception {
        ApplicationDatabaseContext context = open("questions.sqlite");
        User administrator = createAdministrator(context);
        Question second = question("question:b", administrator.id());
        Question first = question("question:a", administrator.id());

        assertEquals(second, context.questionRepository().create(second));
        assertEquals(first, context.questionRepository().create(first));
        assertEquals(List.of(first, second), context.questionRepository().findAll());
        assertEquals(first, context.questionRepository().findById(first.questionId()).orElseThrow());

        Question archived = new Question(
                first.questionId(), QuestionLifecycleState.ARCHIVED, administrator.id(), NOW, LATER
        );
        assertEquals(archived, context.questionRepository().update(archived));
        assertEquals(archived, context.questionRepository()
                .findById(first.questionId()).orElseThrow());
    }

    @Test
    void appendsVersionsWithOrderedOptionsAndReconstructsThemDeterministically()
            throws Exception {
        ApplicationDatabaseContext context = open("versions.sqlite");
        User administrator = createAdministrator(context);
        Question question = context.questionRepository().create(
                question("question:versioned", administrator.id())
        );
        QuestionVersion second = version(
                "version:2", question.questionId(), 2, administrator.id(),
                List.of(
                        new QuestionOption("second-wrong", 0, "Wrong", false),
                        new QuestionOption("second-correct", 1, "Correct", true)
                )
        );
        QuestionVersion first = version(
                "version:1", question.questionId(), 1, administrator.id(),
                List.of(
                        new QuestionOption("first-correct", 0, "Correct", true),
                        new QuestionOption("first-wrong", 1, "Wrong", false)
                )
        );

        context.questionVersionRepository().append(second);
        context.questionVersionRepository().append(first);

        assertEquals(first, context.questionVersionRepository()
                .findById(first.versionId()).orElseThrow());
        assertEquals(List.of(first, second), context.questionVersionRepository()
                .findByQuestionId(question.questionId()));
        assertEquals(1, first.options().stream().filter(QuestionOption::correct).count());
    }

    @Test
    void failedOptionInsertRollsBackVersionAndEveryOption() throws Exception {
        Path database = temporaryDirectory.resolve("version-rollback.sqlite");
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(database);
        User administrator = createAdministrator(context);
        Question question = context.questionRepository().create(
                question("question:rollback", administrator.id())
        );
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(database);
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TRIGGER reject_second_test_option
                    BEFORE INSERT ON question_options
                    WHEN NEW.display_order = 1
                    BEGIN
                        SELECT RAISE(ABORT, 'forced option failure');
                    END
                    """);
        }
        QuestionVersion version = version(
                "version:rollback", question.questionId(), 1, administrator.id(),
                List.of(
                        new QuestionOption("correct", 0, "Correct", true),
                        new QuestionOption("wrong", 1, "Wrong", false)
                )
        );

        assertThrows(
                RepositoryAccessException.class,
                () -> context.questionVersionRepository().append(version)
        );
        assertTrue(context.questionVersionRepository().findById(version.versionId()).isEmpty());
        assertEquals(0, countRows(provider, "question_versions"));
        assertEquals(0, countRows(provider, "question_options"));
    }

    @Test
    void persistsNullableMediaAndAssessmentMetadataAndArchiveState() throws Exception {
        ApplicationDatabaseContext context = open("metadata.sqlite");
        User administrator = createAdministrator(context);
        MediaMetadata media = new MediaMetadata(
                "media:1",
                MediaReferenceType.PASSPORT_CATALOGUE,
                "Ukraine/passport.png",
                "image/png",
                OptionalLong.empty(),
                Optional.empty(),
                administrator.id(),
                NOW,
                NOW,
                false
        );
        AssessmentDefinition assessment = new AssessmentDefinition(
                "assessment:1",
                AssessmentState.DRAFT,
                "Border documents",
                Optional.empty(),
                administrator.id(),
                NOW,
                NOW
        );

        assertEquals(media, context.mediaRepository().create(media));
        assertEquals(assessment, context.assessmentRepository().create(assessment));

        MediaMetadata archivedMedia = new MediaMetadata(
                media.mediaId(), media.referenceType(), media.reference(), media.mimeType(),
                OptionalLong.of(512), Optional.of("a".repeat(64)), administrator.id(),
                NOW, LATER, true
        );
        AssessmentDefinition archivedAssessment = new AssessmentDefinition(
                assessment.assessmentId(), AssessmentState.ARCHIVED, assessment.title(),
                Optional.of("Archived foundation"), administrator.id(), NOW, LATER
        );
        context.mediaRepository().update(archivedMedia);
        context.assessmentRepository().update(archivedAssessment);

        assertAll(
                () -> assertEquals(List.of(archivedMedia), context.mediaRepository().findAll()),
                () -> assertEquals(archivedMedia, context.mediaRepository()
                        .findById(media.mediaId()).orElseThrow()),
                () -> assertEquals(
                        List.of(archivedAssessment), context.assessmentRepository().findAll()
                ),
                () -> assertEquals(archivedAssessment, context.assessmentRepository()
                        .findById(assessment.assessmentId()).orElseThrow()),
                () -> assertTrue(archivedMedia.archived()),
                () -> assertEquals(AssessmentState.ARCHIVED, archivedAssessment.state())
        );
    }

    @Test
    void preservesForeignKeysAndMapsJdbcFailuresToRepositoryException() throws Exception {
        ApplicationDatabaseContext context = open("foreign-key.sqlite");
        Question orphan = question("question:orphan", 999);

        assertThrows(
                RepositoryAccessException.class,
                () -> context.questionRepository().create(orphan)
        );
        assertFalse(context.questionRepository().findById(orphan.questionId()).isPresent());

        DatabaseConnectionProvider unavailable = () -> {
            throw new SQLException("database unavailable");
        };
        assertAll(
                () -> assertThrows(
                        RepositoryAccessException.class,
                        () -> new SQLiteQuestionRepository(unavailable).findAll()
                ),
                () -> assertThrows(
                        RepositoryAccessException.class,
                        () -> new SQLiteQuestionVersionRepository(unavailable)
                                .findByQuestionId("question:any")
                ),
                () -> assertThrows(
                        RepositoryAccessException.class,
                        () -> new SQLiteMediaRepository(unavailable).findAll()
                ),
                () -> assertThrows(
                        RepositoryAccessException.class,
                        () -> new SQLiteAssessmentRepository(unavailable).findAll()
                )
        );
    }

    private ApplicationDatabaseContext open(String filename) throws Exception {
        return ApplicationDatabaseContext.open(temporaryDirectory.resolve(filename));
    }

    private static User createAdministrator(ApplicationDatabaseContext context) {
        return context.userRepository().create(new NewUser(
                "admin", "Administrator", "encoded-test-hash", UserRole.ADMIN, NOW
        ));
    }

    private static Question question(String questionId, long administratorId) {
        return new Question(
                questionId, QuestionLifecycleState.DRAFT, administratorId, NOW, NOW
        );
    }

    private static QuestionVersion version(
            String versionId,
            String questionId,
            int versionNumber,
            long administratorId,
            List<QuestionOption> options
    ) {
        return new QuestionVersion(
                versionId,
                questionId,
                versionNumber,
                AuthoredQuestionType.THEORETICAL,
                "Select the correct answer",
                Optional.of("Choose one option"),
                Optional.of("Only one option is correct"),
                Optional.of("basic"),
                options,
                administratorId,
                NOW
        );
    }

    private static int countRows(SQLiteConnectionProvider provider, String table)
            throws SQLException {
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
