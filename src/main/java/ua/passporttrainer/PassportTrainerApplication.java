package ua.passporttrainer;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ResourceBundle;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;

import javafx.application.Application;
import javafx.stage.Stage;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.controller.ApplicationControllerFactory;
import ua.passporttrainer.controller.JavaFxSessionAbandonmentConfirmation;
import ua.passporttrainer.database.ApplicationDatabaseContext;
import ua.passporttrainer.image.FileSystemPassportImageLocator;
import ua.passporttrainer.image.PassportImageLocator;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.runtime.ApplicationLocations;
import ua.passporttrainer.service.GeneratedQuestionSetProvider;
import ua.passporttrainer.service.QuizSessionCoordinator;
import ua.passporttrainer.service.QuizSessionLifecycleService;

/**
 * JavaFX application entry point for Passport Trainer.
 */
public final class PassportTrainerApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");
        ApplicationLocations locations = ApplicationLocations.fromSystemProperties();
        PassportImageLocator imageLocator = new FileSystemPassportImageLocator(locations.passportAssetsRoot());
        ApplicationControllerFactory controllerFactory;
        View initialView = View.START;
        try {
            ApplicationDatabaseContext applicationDatabaseContext =
                    ApplicationDatabaseContext.open(locations.applicationDatabasePath());
            CatalogueApplicationContext catalogueContext =
                    CatalogueApplicationContext.openReadOnly(locations.cataloguePath());
            Clock sessionClock = Clock.systemUTC();
            AtomicLong sessionIds = new AtomicLong(System.currentTimeMillis());
            SplittableRandom sessionSeeds = new SplittableRandom();
            QuizSessionCoordinator sessionCoordinator = new QuizSessionCoordinator(
                    new GeneratedQuestionSetProvider(catalogueContext.quizGenerationService()),
                    new QuizSessionLifecycleService(sessionClock),
                    () -> applicationDatabaseContext.currentUserSession().currentUserIdOr(
                            QuizSessionCoordinator.ANONYMOUS_USER_ID
                    ),
                    sessionIds::incrementAndGet,
                    sessionSeeds::nextLong
            );
            controllerFactory = ApplicationControllerFactory.available(
                    catalogueContext,
                    sessionCoordinator,
                    applicationDatabaseContext.examResultService(),
                    applicationDatabaseContext.accountSessionService(),
                    imageLocator,
                    new JavaFxSessionAbandonmentConfirmation(messages),
                    messages
            );
            initialView = applicationDatabaseContext.accountSessionService()
                    .initialAdministratorRequired()
                    ? View.FIRST_RUN_ADMINISTRATOR
                    : View.LOGIN;
        } catch (IOException | SQLException | RuntimeException exception) {
            controllerFactory = ApplicationControllerFactory.unavailable(
                    exception, imageLocator, messages
            );
        }
        NavigationService navigationService = new NavigationService(primaryStage, messages, controllerFactory);

        primaryStage.setTitle(messages.getString("app.title"));
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(560);
        navigationService.show(initialView);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
