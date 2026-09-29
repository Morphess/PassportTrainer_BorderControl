package ua.passporttrainer.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.database.ApplicationDatabaseContext;
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
import ua.passporttrainer.security.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminServicesAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final List<QuestionOption> OPTIONS = List.of(
            new QuestionOption("correct", 0, "Correct", true),
            new QuestionOption("wrong", 1, "Wrong", false)
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void activeAdministratorCanUseEveryAdminFoundationOperation() throws Exception {
        ApplicationDatabaseContext context = open("administrator.sqlite");
        User administrator = createUser(context, "admin", UserRole.ADMIN);
        context.currentUserSession().login(AuthenticatedUser.from(administrator));

        Question question = context.adminQuestionService().createDraftQuestion();
        assertEquals(question, context.adminQuestionService()
                .findQuestion(question.questionId()).orElseThrow());
        assertEquals(List.of(question), context.adminQuestionService().listQuestions());
        QuestionVersion version = context.adminQuestionService().createQuestionVersion(
                question.questionId(),
                1,
                AuthoredQuestionType.THEORETICAL,
                "Select the correct answer",
                Optional.empty(),
                Optional.of("One correct option"),
                Optional.of("basic"),
                OPTIONS
        );
        assertEquals(version, context.adminQuestionService()
                .findQuestionVersion(version.versionId()).orElseThrow());
        assertEquals(List.of(version), context.adminQuestionService()
                .listQuestionVersions(question.questionId()));
        Question archivedQuestion = context.adminQuestionService()
                .archiveQuestion(question.questionId());

        AssessmentDefinition assessment = context.adminAssessmentService()
                .createDraftAssessment("Assessment", Optional.of("Foundation"));
        assertEquals(assessment, context.adminAssessmentService()
                .findAssessment(assessment.assessmentId()).orElseThrow());
        assertEquals(
                List.of(assessment), context.adminAssessmentService().listAssessments()
        );
        AssessmentDefinition archivedAssessment = context.adminAssessmentService()
                .archiveAssessment(assessment.assessmentId());

        MediaMetadata media = context.adminMediaService().createMetadata(
                MediaReferenceType.UPLOADED_FILE,
                "admin-media/example.png",
                "image/png",
                OptionalLong.of(128),
                Optional.of("a".repeat(64))
        );
        assertEquals(media, context.adminMediaService()
                .findMetadata(media.mediaId()).orElseThrow());
        assertEquals(List.of(media), context.adminMediaService().listMetadata());
        MediaMetadata archivedMedia = context.adminMediaService()
                .archiveMetadata(media.mediaId());

        assertAll(
                () -> assertEquals(administrator.id(), question.createdBy()),
                () -> assertEquals(administrator.id(), version.createdBy()),
                () -> assertEquals(administrator.id(), assessment.createdBy()),
                () -> assertEquals(administrator.id(), media.createdBy()),
                () -> assertEquals(
                        QuestionLifecycleState.ARCHIVED, archivedQuestion.lifecycleState()
                ),
                () -> assertEquals(AssessmentState.ARCHIVED, archivedAssessment.state()),
                () -> assertTrue(archivedMedia.archived())
        );
    }

    @Test
    void ordinaryUserIsDeniedBeforeEveryAdminRepositoryOperation() throws Exception {
        ApplicationDatabaseContext context = open("ordinary-user.sqlite");
        User user = createUser(context, "ordinary", UserRole.USER);
        context.currentUserSession().login(AuthenticatedUser.from(user));

        assertEveryAdminOperationDeniedWithoutMutation(context);
    }

    @Test
    void anonymousPrincipalIsDeniedBeforeEveryAdminRepositoryOperation() throws Exception {
        ApplicationDatabaseContext context = open("anonymous.sqlite");

        assertEveryAdminOperationDeniedWithoutMutation(context);
    }

    @Test
    void inactiveAdministratorIsDeniedBeforeEveryAdminRepositoryOperation() throws Exception {
        ApplicationDatabaseContext context = open("inactive-administrator.sqlite");
        User active = createUser(context, "inactive-admin", UserRole.ADMIN);
        context.currentUserSession().login(AuthenticatedUser.from(active));
        context.userRepository().update(new User(
                active.id(),
                active.username(),
                active.displayName(),
                active.passwordHash(),
                active.role(),
                false,
                active.createdAt(),
                NOW.plusSeconds(1)
        ));

        assertEveryAdminOperationDeniedWithoutMutation(context);
    }

    private static void assertEveryAdminOperationDeniedWithoutMutation(
            ApplicationDatabaseContext context
    ) {
        for (Executable operation : everyAdminOperation(context)) {
            assertThrows(AuthorizationException.class, operation);
        }
        assertAll(
                () -> assertTrue(context.questionRepository().findAll().isEmpty()),
                () -> assertTrue(context.questionVersionRepository()
                        .findByQuestionId("question:missing").isEmpty()),
                () -> assertTrue(context.assessmentRepository().findAll().isEmpty()),
                () -> assertTrue(context.mediaRepository().findAll().isEmpty())
        );
    }

    private static List<Executable> everyAdminOperation(ApplicationDatabaseContext context) {
        return List.of(
                () -> context.adminQuestionService().createDraftQuestion(),
                () -> context.adminQuestionService().findQuestion("question:missing"),
                () -> context.adminQuestionService().listQuestions(),
                () -> context.adminQuestionService().archiveQuestion("question:missing"),
                () -> context.adminQuestionService().createQuestionVersion(
                        "question:missing",
                        1,
                        AuthoredQuestionType.THEORETICAL,
                        "Prompt",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        OPTIONS
                ),
                () -> context.adminQuestionService()
                        .findQuestionVersion("question-version:missing"),
                () -> context.adminQuestionService()
                        .listQuestionVersions("question:missing"),
                () -> context.adminAssessmentService()
                        .createDraftAssessment("Assessment", Optional.empty()),
                () -> context.adminAssessmentService()
                        .findAssessment("assessment:missing"),
                () -> context.adminAssessmentService().listAssessments(),
                () -> context.adminAssessmentService()
                        .archiveAssessment("assessment:missing"),
                () -> context.adminMediaService().createMetadata(
                        MediaReferenceType.PASSPORT_CATALOGUE,
                        "Ukraine/passport.png",
                        "image/png",
                        OptionalLong.empty(),
                        Optional.empty()
                ),
                () -> context.adminMediaService().findMetadata("media:missing"),
                () -> context.adminMediaService().listMetadata(),
                () -> context.adminMediaService().archiveMetadata("media:missing")
        );
    }

    private ApplicationDatabaseContext open(String filename) throws Exception {
        return ApplicationDatabaseContext.open(temporaryDirectory.resolve(filename));
    }

    private static User createUser(
            ApplicationDatabaseContext context,
            String username,
            UserRole role
    ) {
        return context.userRepository().create(new NewUser(
                username, username, "encoded-test-hash", role, NOW
        ));
    }
}
