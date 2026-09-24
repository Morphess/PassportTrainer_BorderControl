package ua.passporttrainer.controller;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import ua.passporttrainer.model.QuizSessionLifecycle;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.TrainingGenerationFailureFormatter;
import ua.passporttrainer.presentation.TrainingResultPresentation;
import ua.passporttrainer.service.QuizGenerationException;
import ua.passporttrainer.service.QuizSessionCoordinator;

/** Presents one completed in-memory Training result and restart actions. */
public final class TrainingResultController {

    private final NavigationService navigationService;
    private final QuizSessionCoordinator sessionCoordinator;
    private final TrainingGenerationFailureFormatter failureFormatter;
    private final ResourceBundle resources;

    @FXML
    private Label correctCountLabel;
    @FXML
    private Label incorrectCountLabel;
    @FXML
    private Label totalCountLabel;
    @FXML
    private Label percentageLabel;
    @FXML
    private Label errorLabel;

    public TrainingResultController(
            NavigationService navigationService,
            QuizSessionCoordinator sessionCoordinator,
            TrainingGenerationFailureFormatter failureFormatter,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.sessionCoordinator = Objects.requireNonNull(sessionCoordinator, "sessionCoordinator");
        this.failureFormatter = Objects.requireNonNull(failureFormatter, "failureFormatter");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(correctCountLabel, "correctCountLabel was not injected");
        Objects.requireNonNull(incorrectCountLabel, "incorrectCountLabel was not injected");
        Objects.requireNonNull(totalCountLabel, "totalCountLabel was not injected");
        Objects.requireNonNull(percentageLabel, "percentageLabel was not injected");
        Objects.requireNonNull(errorLabel, "errorLabel was not injected");
        if (sessionCoordinator.lifecycle().orElseThrow() != QuizSessionLifecycle.COMPLETED) {
            throw new IllegalStateException("Training result requires a completed session");
        }

        int total = sessionCoordinator.session().orElseThrow().questions().size();
        int correct = sessionCoordinator.visibleScore().orElseThrow();
        TrainingResultPresentation result = TrainingResultPresentation.fromCounts(correct, total);
        correctCountLabel.setText(Integer.toString(result.correctCount()));
        incorrectCountLabel.setText(Integer.toString(result.incorrectCount()));
        totalCountLabel.setText(Integer.toString(result.totalCount()));
        percentageLabel.setText(MessageFormat.format(
                resources.getString("training.result.percentageValue"),
                result.percentage()
        ));
        setError(null);
    }

    @FXML
    private void repeatTraining() {
        try {
            sessionCoordinator.restart();
        } catch (QuizGenerationException failure) {
            setError(failureFormatter.format(failure));
            return;
        } catch (RuntimeException failure) {
            setError(resources.getString("training.failure.unexpected"));
            return;
        }
        navigationService.replace(View.QUESTION);
    }

    @FXML
    private void newTraining() {
        sessionCoordinator.abandon();
        navigationService.goBack();
    }

    @FXML
    private void openMainMenu() {
        sessionCoordinator.abandon();
        navigationService.resetTo(View.START);
    }

    private void setError(String message) {
        boolean visible = message != null && !message.isBlank();
        errorLabel.setText(visible ? message : "");
        errorLabel.setVisible(visible);
        errorLabel.setManaged(visible);
    }
}
