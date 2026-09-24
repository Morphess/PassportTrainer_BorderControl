package ua.passporttrainer.controller;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import ua.passporttrainer.model.AssessmentSource;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.model.QuizSessionLifecycle;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.ExaminationResultPresentation;
import ua.passporttrainer.repository.RepositoryAccessException;
import ua.passporttrainer.service.ExamResultService;
import ua.passporttrainer.service.QuizSessionCoordinator;

/** Presents and persists one completed Examination result. */
public final class ExaminationResultController {

    private final NavigationService navigationService;
    private final QuizSessionCoordinator sessionCoordinator;
    private final ExamResultService examResultService;
    private final ResourceBundle resources;
    private QuizSession completedSession;

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
    @FXML
    private Label persistenceStatusLabel;
    @FXML
    private Button retrySaveButton;
    @FXML
    private Button newExaminationButton;
    @FXML
    private Button mainMenuButton;

    public ExaminationResultController(
            NavigationService navigationService,
            QuizSessionCoordinator sessionCoordinator,
            ExamResultService examResultService,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.sessionCoordinator = Objects.requireNonNull(sessionCoordinator, "sessionCoordinator");
        this.examResultService = Objects.requireNonNull(examResultService, "examResultService");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(correctCountLabel, "correctCountLabel was not injected");
        Objects.requireNonNull(incorrectCountLabel, "incorrectCountLabel was not injected");
        Objects.requireNonNull(totalCountLabel, "totalCountLabel was not injected");
        Objects.requireNonNull(percentageLabel, "percentageLabel was not injected");
        Objects.requireNonNull(durationLabel, "durationLabel was not injected");
        Objects.requireNonNull(persistenceStatusLabel, "persistenceStatusLabel was not injected");
        Objects.requireNonNull(retrySaveButton, "retrySaveButton was not injected");
        Objects.requireNonNull(newExaminationButton, "newExaminationButton was not injected");
        Objects.requireNonNull(mainMenuButton, "mainMenuButton was not injected");
        if (sessionCoordinator.lifecycle().orElseThrow() != QuizSessionLifecycle.COMPLETED) {
            throw new IllegalStateException("Examination result requires a completed session");
        }

        completedSession = sessionCoordinator.session().orElseThrow();
        ExaminationResultPresentation result = ExaminationResultPresentation.fromCompletedSession(
                completedSession,
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
        persistResult();
    }

    @FXML
    private void retrySave() {
        persistResult();
    }

    private void persistResult() {
        try {
            examResultService.saveExamResult(
                    completedSession.id(),
                    completedSession,
                    AssessmentSource.GENERATED,
                    sessionCoordinator.assessmentProvenance()
            );
            showPersistenceState("examination.result.saveSuccess", false);
            retrySaveButton.setVisible(false);
            retrySaveButton.setManaged(false);
            newExaminationButton.setDisable(false);
            mainMenuButton.setDisable(false);
        } catch (RepositoryAccessException exception) {
            showPersistenceState("examination.result.saveFailure", true);
            retrySaveButton.setVisible(true);
            retrySaveButton.setManaged(true);
            newExaminationButton.setDisable(true);
            mainMenuButton.setDisable(true);
        }
    }

    private void showPersistenceState(String resourceKey, boolean failure) {
        persistenceStatusLabel.setText(resources.getString(resourceKey));
        persistenceStatusLabel.getStyleClass().removeAll("support-label", "error-label");
        persistenceStatusLabel.getStyleClass().add(failure ? "error-label" : "support-label");
        persistenceStatusLabel.setVisible(true);
        persistenceStatusLabel.setManaged(true);
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
