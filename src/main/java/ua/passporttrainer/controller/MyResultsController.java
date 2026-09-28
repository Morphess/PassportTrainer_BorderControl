package ua.passporttrainer.controller;

import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.presentation.ResultHistoryItemPresentation;
import ua.passporttrainer.presentation.UserResultsPresentationMapper;
import ua.passporttrainer.presentation.UserStatisticsPresentation;
import ua.passporttrainer.repository.RepositoryAccessException;
import ua.passporttrainer.service.AuthorizationException;
import ua.passporttrainer.service.CurrentUserResultsService;
import ua.passporttrainer.service.UserResultsSnapshot;

/** Presents only the current active account's completed Examination history. */
public final class MyResultsController {

    private final NavigationService navigationService;
    private final CurrentUserResultsService currentUserResultsService;
    private final UserResultsPresentationMapper presentationMapper;
    private final ResourceBundle resources;

    @FXML
    private VBox statisticsPanel;
    @FXML
    private Label completedExaminationsLabel;
    @FXML
    private Label averagePercentageLabel;
    @FXML
    private Label bestPercentageLabel;
    @FXML
    private Label totalAnsweredLabel;
    @FXML
    private Label totalCorrectLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TableView<ResultHistoryItemPresentation> resultsTable;
    @FXML
    private TableColumn<ResultHistoryItemPresentation, String> completedAtColumn;
    @FXML
    private TableColumn<ResultHistoryItemPresentation, String> assessmentColumn;
    @FXML
    private TableColumn<ResultHistoryItemPresentation, String> totalColumn;
    @FXML
    private TableColumn<ResultHistoryItemPresentation, String> correctColumn;
    @FXML
    private TableColumn<ResultHistoryItemPresentation, String> percentageColumn;
    @FXML
    private TableColumn<ResultHistoryItemPresentation, String> durationColumn;

    public MyResultsController(
            NavigationService navigationService,
            CurrentUserResultsService currentUserResultsService,
            UserResultsPresentationMapper presentationMapper,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.currentUserResultsService = Objects.requireNonNull(
                currentUserResultsService, "currentUserResultsService"
        );
        this.presentationMapper = Objects.requireNonNull(
                presentationMapper, "presentationMapper"
        );
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        requireInjectedFields();
        configureColumns();
        showStatus("results.loading", false);
        setTableVisible(false);
        setStatisticsVisible(false);
        loadCurrentUserResults();
    }

    private void loadCurrentUserResults() {
        try {
            Optional<UserResultsSnapshot> currentResults =
                    currentUserResultsService.findCurrentUserResults();
            if (currentResults.isEmpty()) {
                showStatus("results.guest", false);
                return;
            }
            UserResultsSnapshot snapshot = currentResults.orElseThrow();
            showStatistics(presentationMapper.mapStatistics(snapshot.statistics()));
            resultsTable.getItems().clear();
            for (var result : snapshot.results()) {
                resultsTable.getItems().add(presentationMapper.mapResult(result));
            }
            if (snapshot.results().isEmpty()) {
                showStatus("results.empty", false);
            } else {
                hideStatus();
                setTableVisible(true);
            }
        } catch (AuthorizationException exception) {
            showStatus("results.inactive", true);
        } catch (RepositoryAccessException exception) {
            showStatus("results.loadFailure", true);
        }
    }

    private void configureColumns() {
        resultsTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );
        completedAtColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().completedAt()));
        assessmentColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().assessment()));
        totalColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().totalQuestions()));
        correctColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().correctAnswers()));
        percentageColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().percentage()));
        durationColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().duration()));
    }

    private void showStatistics(UserStatisticsPresentation statistics) {
        completedExaminationsLabel.setText(statistics.completedExaminations());
        averagePercentageLabel.setText(statistics.averagePercentage());
        bestPercentageLabel.setText(statistics.bestPercentage());
        totalAnsweredLabel.setText(statistics.totalAnswered());
        totalCorrectLabel.setText(statistics.totalCorrect());
        setStatisticsVisible(true);
    }

    private void showStatus(String resourceKey, boolean error) {
        statusLabel.setText(resources.getString(resourceKey));
        statusLabel.getStyleClass().removeAll("support-label", "error-label");
        statusLabel.getStyleClass().add(error ? "error-label" : "support-label");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void hideStatus() {
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private void setTableVisible(boolean visible) {
        resultsTable.setVisible(visible);
        resultsTable.setManaged(visible);
    }

    private void setStatisticsVisible(boolean visible) {
        statisticsPanel.setVisible(visible);
        statisticsPanel.setManaged(visible);
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }

    private void requireInjectedFields() {
        Objects.requireNonNull(statisticsPanel, "statisticsPanel was not injected");
        Objects.requireNonNull(
                completedExaminationsLabel, "completedExaminationsLabel was not injected"
        );
        Objects.requireNonNull(averagePercentageLabel, "averagePercentageLabel was not injected");
        Objects.requireNonNull(bestPercentageLabel, "bestPercentageLabel was not injected");
        Objects.requireNonNull(totalAnsweredLabel, "totalAnsweredLabel was not injected");
        Objects.requireNonNull(totalCorrectLabel, "totalCorrectLabel was not injected");
        Objects.requireNonNull(statusLabel, "statusLabel was not injected");
        Objects.requireNonNull(resultsTable, "resultsTable was not injected");
        Objects.requireNonNull(completedAtColumn, "completedAtColumn was not injected");
        Objects.requireNonNull(assessmentColumn, "assessmentColumn was not injected");
        Objects.requireNonNull(totalColumn, "totalColumn was not injected");
        Objects.requireNonNull(correctColumn, "correctColumn was not injected");
        Objects.requireNonNull(percentageColumn, "percentageColumn was not injected");
        Objects.requireNonNull(durationColumn, "durationColumn was not injected");
    }
}
