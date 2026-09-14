package ua.passporttrainer;

import java.util.ResourceBundle;

import javafx.application.Application;
import javafx.stage.Stage;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;

/**
 * JavaFX application entry point for Passport Trainer.
 */
public final class PassportTrainerApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");
        NavigationService navigationService = new NavigationService(primaryStage, messages);

        primaryStage.setTitle(messages.getString("app.title"));
        primaryStage.setMinWidth(480);
        primaryStage.setMinHeight(300);
        navigationService.show(View.START);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
