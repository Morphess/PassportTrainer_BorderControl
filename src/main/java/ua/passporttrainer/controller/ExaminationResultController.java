package ua.passporttrainer.controller;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.model.QuizSessionLifecycle;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.ExaminationResultPresentation;
import ua.passporttrainer.service.QuizSessionCoordinator;

/** Presents one completed in-memory Examination result. */
public final class ExaminationResultController {

    private final NavigationService navigationService;
    private final QuizSessionCoordinator sessionCoordinator;
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
    private Label durationLabel;

    public ExaminationResultController(
            NavigationService navigationService,
            QuizSessionCoordinator sessionCoordinator,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.sessionCoordinator = Objects.requireNonNull(sessionCoordinator, "sessionCoordinator");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(correctCountLabel, "correctCountLabel was not injected");
        Objects.requireNonNull(incorrectCountLabel, "incorrectCountLabel was not injected");
        Objects.requireNonNull(totalCountLabel, "totalCountLabel was not injected");
        Objects.requireNonNull(percentageLabel, "percentageLabel was not injected");
        Objects.requireNonNull(durationLabel, "durationLabel was not injected");
        if (sessionCoordinator.lifecycle().orElseThrow() != QuizSessionLifecycle.COMPLETED) {
            throw new IllegalStateException("Examination result requires a completed session");
        }

        QuizSession session = sessionCoordinator.session().orElseThrow();
        ExaminationResultPresentation result = ExaminationResultPresentation.fromCompletedSession(
                session,
                sessionCoordinator.visibleScore().orElseThrow()
        );
        correctCountLabel.setText(Integer.toString(result.correctCount()));
        incorrectCountLabel.setText(Integer.toString(result.incorrectCount()));
        totalCountLabel.setText(Integer.toString(result.totalCount()));
        percentageLabel.setText(MessageFormat.format(
                resources.getString("examination.result.percentageValue"),
                result.percentage()
        ));
        durationLabel.setText(MessageFormat.format(
                resources.getString("examination.result.durationValue"),
                result.durationSeconds()
        ));
    }

    @FXML
    private void newExamination() {
        sessionCoordinator.abandon();
        navigationService.goBack();
    }

    @FXML
    private void openMainMenu() {
        sessionCoordinator.abandon();
        navigationService.resetTo(View.START);
    }
}
