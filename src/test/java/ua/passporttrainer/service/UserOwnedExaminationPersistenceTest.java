package ua.passporttrainer.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.database.ApplicationDatabaseContext;
import ua.passporttrainer.model.AssessmentSource;
import ua.passporttrainer.model.NewUser;
import ua.passporttrainer.model.QuestionSet;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.model.QuizSessionConfiguration;
import ua.passporttrainer.model.User;
import ua.passporttrainer.model.UserRole;
import ua.passporttrainer.security.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserOwnedExaminationPersistenceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void loggedInExaminationPersistsTheCapturedRealUserForeignKey() throws Exception {
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(
                temporaryDirectory.resolve("owned-examination.sqlite")
        );
        User user = context.userRepository().create(new NewUser(
                "examiner", "Examiner", "encoded-test-hash", UserRole.USER, CLOCK.instant()
        ));
        context.currentUserSession().login(AuthenticatedUser.from(user));
        QuizQuestion question = QuizSessionLifecycleServiceTest.question(1, "entity:ukraine");
        QuizSessionCoordinator coordinator = new QuizSessionCoordinator(
                ignored -> new QuestionSet(List.of(question), Optional.empty()),
                new QuizSessionLifecycleService(CLOCK),
                () -> context.currentUserSession().currentUserIdOr(
                        QuizSessionCoordinator.ANONYMOUS_USER_ID
                ),
                () -> 500L,
                () -> 600L
        );
        coordinator.configure(new QuizSessionConfiguration(
                QuizMode.EXAMINATION,
                1,
                question.options().size(),
                Set.of(question.type()),
                Set.of(),
                Set.of(),
                Set.of()
        ));

        coordinator.start();
        coordinator.submitAnswer(question.correctAnswerId());
        QuizSession completed = coordinator.complete();
        context.currentUserSession().logout();
        context.examResultService().saveExamResult(
                700L,
                completed,
                AssessmentSource.GENERATED,
                coordinator.assessmentProvenance()
        );

        assertEquals(
                user.id(),
                context.resultRepository().findById(700L).orElseThrow().userId()
        );
    }

    @Test
    void anonymousExaminationPersistsNullOwnership() throws Exception {
        ApplicationDatabaseContext context = ApplicationDatabaseContext.open(
                temporaryDirectory.resolve("anonymous-examination.sqlite")
        );
        QuizQuestion question = QuizSessionLifecycleServiceTest.question(1, "entity:ukraine");
        QuizSessionCoordinator coordinator = new QuizSessionCoordinator(
                ignored -> new QuestionSet(List.of(question), Optional.empty()),
                new QuizSessionLifecycleService(CLOCK),
                () -> context.currentUserSession().currentUserIdOr(
                        QuizSessionCoordinator.ANONYMOUS_USER_ID
                ),
                () -> 501L,
                () -> 601L
        );
        coordinator.configure(new QuizSessionConfiguration(
                QuizMode.EXAMINATION,
                1,
                question.options().size(),
                Set.of(question.type()),
                Set.of(),
                Set.of(),
                Set.of()
        ));

        coordinator.start();
        assertEquals(
                QuizSessionCoordinator.ANONYMOUS_USER_ID,
                coordinator.session().orElseThrow().userId()
        );
        coordinator.submitAnswer(question.correctAnswerId());
        QuizSession completed = coordinator.complete();
        context.examResultService().saveExamResult(
                701L,
                completed,
                AssessmentSource.GENERATED,
                coordinator.assessmentProvenance()
        );

        assertNull(context.resultRepository().findById(701L).orElseThrow().userId());
    }
}
