package ua.passporttrainer.controller;

import java.util.Objects;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.QuizGenerationFailureFormatter;
import ua.passporttrainer.service.ExaminationConfigurationFactory;
import ua.passporttrainer.service.QuizGenerationException;
import ua.passporttrainer.service.QuizSessionCoordinator;

/** Confirms the fixed generated Examination policy and starts one session. */
public final class ExaminationConfirmationController {

    private final NavigationService navigationService;
    private final QuizSessionCoordinator sessionCoordinator;
    private final ExaminationConfigurationFactory configurationFactory;
    private final QuizGenerationFailureFormatter failureFormatter;
    private final ResourceBundle resources;

    @FXML
    private Label errorLabel;

    public ExaminationConfirmationController(
            NavigationService navigationService,
            QuizSessionCoordinator sessionCoordinator,
            ExaminationConfigurationFactory configurationFactory,
            QuizGenerationFailureFormatter failureFormatter,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.sessionCoordinator = Objects.requireNonNull(sessionCoordinator, "sessionCoordinator");
        this.configurationFactory = Objects.requireNonNull(
                configurationFactory, "configurationFactory"
        );
        this.failureFormatter = Objects.requireNonNull(failureFormatter, "failureFormatter");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(errorLabel, "errorLabel was not injected");
        setError(null);
    }

    @FXML
    private void startExamination() {
        try {
            sessionCoordinator.configure(configurationFactory.create());
            sessionCoordinator.start();
        } catch (QuizGenerationException failure) {
            setError(failureFormatter.format(failure));
            return;
        } catch (RuntimeException failure) {
            setError(resources.getString("examination.failure.unexpected"));
            return;
        }
        navigationService.show(View.QUESTION);
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }

    private void setError(String message) {
        boolean visible = message != null && !message.isBlank();
        errorLabel.setText(visible ? message : "");
        errorLabel.setVisible(visible);
        errorLabel.setManaged(visible);
    }
}
