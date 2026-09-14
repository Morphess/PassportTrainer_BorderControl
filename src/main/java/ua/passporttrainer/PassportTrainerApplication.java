package ua.passporttrainer;

import java.sql.SQLException;
import java.util.ResourceBundle;

import javafx.application.Application;
import javafx.stage.Stage;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.controller.CatalogueControllerFactory;
import ua.passporttrainer.image.FileSystemPassportImageLocator;
import ua.passporttrainer.image.PassportImageLocator;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.runtime.ApplicationLocations;

/**
 * JavaFX application entry point for Passport Trainer.
 */
public final class PassportTrainerApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");
        ApplicationLocations locations = ApplicationLocations.fromSystemProperties();
        PassportImageLocator imageLocator = new FileSystemPassportImageLocator(locations.passportAssetsRoot());
        CatalogueControllerFactory controllerFactory;
        try {
            CatalogueApplicationContext catalogueContext =
                    CatalogueApplicationContext.openReadOnly(locations.cataloguePath());
            controllerFactory = CatalogueControllerFactory.available(catalogueContext, imageLocator, messages);
        } catch (SQLException | RuntimeException exception) {
            controllerFactory = CatalogueControllerFactory.unavailable(exception, imageLocator, messages);
        }
        NavigationService navigationService = new NavigationService(primaryStage, messages, controllerFactory);

        primaryStage.setTitle(messages.getString("app.title"));
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(560);
        navigationService.show(View.START);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
