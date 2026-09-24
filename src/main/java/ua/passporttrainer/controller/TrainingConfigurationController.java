package ua.passporttrainer.controller;

import java.text.MessageFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSessionConfiguration;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.LearningEntityPresentation;
import ua.passporttrainer.presentation.TrainingGenerationFailureFormatter;
import ua.passporttrainer.presentation.TrainingScopePresentation;
import ua.passporttrainer.service.LearningService;
import ua.passporttrainer.service.QuizGenerationException;
import ua.passporttrainer.service.QuizSessionCoordinator;
import ua.passporttrainer.service.TrainingConfigurationFactory;

/** Collects editable Training settings and starts one coordinator-owned session. */
public final class TrainingConfigurationController {

    private final NavigationService navigationService;
    private final QuizSessionCoordinator sessionCoordinator;
    private final LearningService learningService;
    private final TrainingConfigurationFactory configurationFactory;
    private final TrainingGenerationFailureFormatter failureFormatter;
    private final ResourceBundle resources;

    @FXML
    private Spinner<Integer> questionCountSpinner;
    @FXML
    private ComboBox<TrainingScopePresentation> scopeComboBox;
    @FXML
    private CheckBox passportToCountryCheckBox;
    @FXML
    private CheckBox countryToPassportCheckBox;
    @FXML
    private CheckBox documentTypeCheckBox;
    @FXML
    private CheckBox findOrdinaryPassportCheckBox;
    @FXML
    private Label capacityLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Button startTrainingButton;

    public TrainingConfigurationController(
            NavigationService navigationService,
            QuizSessionCoordinator sessionCoordinator,
            LearningService learningService,
            TrainingConfigurationFactory configurationFactory,
            TrainingGenerationFailureFormatter failureFormatter,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.sessionCoordinator = Objects.requireNonNull(sessionCoordinator, "sessionCoordinator");
        this.learningService = Objects.requireNonNull(learningService, "learningService");
        this.configurationFactory = Objects.requireNonNull(configurationFactory, "configurationFactory");
        this.failureFormatter = Objects.requireNonNull(failureFormatter, "failureFormatter");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(questionCountSpinner, "questionCountSpinner was not injected");
        Objects.requireNonNull(scopeComboBox, "scopeComboBox was not injected");
        Objects.requireNonNull(passportToCountryCheckBox, "passportToCountryCheckBox was not injected");
        Objects.requireNonNull(countryToPassportCheckBox, "countryToPassportCheckBox was not injected");
        Objects.requireNonNull(documentTypeCheckBox, "documentTypeCheckBox was not injected");
        Objects.requireNonNull(findOrdinaryPassportCheckBox, "findOrdinaryPassportCheckBox was not injected");
        Objects.requireNonNull(capacityLabel, "capacityLabel was not injected");
        Objects.requireNonNull(errorLabel, "errorLabel was not injected");
        Objects.requireNonNull(startTrainingButton, "startTrainingButton was not injected");

        configureScopeCells();
        setError(null);
        try {
            List<LearningEntityPresentation> entities = learningService.findEligibleEntities();
            int totalDocuments = entities.stream()
                    .mapToInt(LearningEntityPresentation::documentCount)
                    .sum();
            scopeComboBox.getItems().add(TrainingScopePresentation.all(
                    resources.getString("training.scope.all"),
                    totalDocuments
            ));
            entities.stream()
                    .map(entity -> TrainingScopePresentation.entity(
                            entity.entityId(),
                            entity.displayName(),
                            entity.documentCount()
                    ))
                    .forEach(scopeComboBox.getItems()::add);
            scopeComboBox.valueProperty().addListener((observable, previous, current) -> updateCapacity(current));
            scopeComboBox.getSelectionModel().selectFirst();
            startTrainingButton.setDisable(totalDocuments == 0);
            if (totalDocuments == 0) {
                setError(resources.getString("training.failure.noEligibleData"));
            }
        } catch (RuntimeException exception) {
            startTrainingButton.setDisable(true);
            capacityLabel.setText("");
            setError(resources.getString("training.failure.loadConfiguration"));
        }
    }

    public void showForEntity(String entityId) {
        Objects.requireNonNull(entityId, "entityId");
        if (entityId.isBlank()) {
            throw new IllegalArgumentException("entityId must not be blank");
        }
        TrainingScopePresentation matchingScope = scopeComboBox.getItems().stream()
                .filter(scope -> scope.entityId().filter(entityId::equals).isPresent())
                .findFirst()
                .orElse(null);
        if (matchingScope == null) {
            scopeComboBox.getSelectionModel().clearSelection();
            startTrainingButton.setDisable(true);
            setError(resources.getString("training.failure.preselectedEntityUnavailable"));
            return;
        }
        scopeComboBox.getSelectionModel().select(matchingScope);
        setError(null);
    }

    @FXML
    private void startTraining() {
        TrainingScopePresentation scope = scopeComboBox.getValue();
        if (scope == null) {
            setError(resources.getString("training.validation.scopeRequired"));
            return;
        }
        Set<QuizQuestionType> questionTypes = selectedQuestionTypes();
        if (questionTypes.isEmpty()) {
            setError(resources.getString("training.validation.questionTypeRequired"));
            return;
        }

        QuizSessionConfiguration configuration;
        try {
            configuration = configurationFactory.create(
                    questionCountSpinner.getValue(),
                    questionTypes,
                    scope.entityId()
            );
        } catch (IllegalArgumentException exception) {
            setError(resources.getString("training.validation.invalidConfiguration"));
            return;
        }

        sessionCoordinator.configure(configuration);
        try {
            sessionCoordinator.start();
        } catch (QuizGenerationException failure) {
            setError(failureFormatter.format(failure));
            return;
        } catch (RuntimeException failure) {
            setError(resources.getString("training.failure.unexpected"));
            return;
        }
        navigationService.show(View.QUESTION);
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }

    private void updateCapacity(TrainingScopePresentation scope) {
        if (scope == null) {
            capacityLabel.setText("");
            return;
        }
        int maximum = scope.documentCount();
        if (maximum <= 0) {
            startTrainingButton.setDisable(true);
            capacityLabel.setText(resources.getString("training.failure.noEligibleData"));
            return;
        }
        int current = questionCountSpinner.getValue() == null
                ? TrainingConfigurationFactory.DEFAULT_QUESTION_COUNT
                : questionCountSpinner.getValue();
        int selected = Math.min(current, maximum);
        questionCountSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maximum, selected)
        );
        capacityLabel.setText(MessageFormat.format(
                resources.getString("training.capacity"),
                maximum
        ));
        startTrainingButton.setDisable(false);
    }

    private Set<QuizQuestionType> selectedQuestionTypes() {
        EnumSet<QuizQuestionType> selected = EnumSet.noneOf(QuizQuestionType.class);
        addIfSelected(selected, passportToCountryCheckBox, QuizQuestionType.PASSPORT_TO_COUNTRY);
        addIfSelected(selected, countryToPassportCheckBox, QuizQuestionType.COUNTRY_TO_PASSPORT);
        addIfSelected(selected, documentTypeCheckBox, QuizQuestionType.DOCUMENT_TYPE);
        addIfSelected(selected, findOrdinaryPassportCheckBox, QuizQuestionType.FIND_ORDINARY_PASSPORT);
        return Set.copyOf(selected);
    }

    private static void addIfSelected(
            Set<QuizQuestionType> selected,
            CheckBox checkBox,
            QuizQuestionType questionType
    ) {
        if (checkBox.isSelected()) {
            selected.add(questionType);
        }
    }

    private void configureScopeCells() {
        scopeComboBox.setCellFactory(ignored -> scopeCell());
        scopeComboBox.setButtonCell(scopeCell());
    }

    private ListCell<TrainingScopePresentation> scopeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(TrainingScopePresentation scope, boolean empty) {
                super.updateItem(scope, empty);
                setText(empty || scope == null ? null : MessageFormat.format(
                        resources.getString("training.scope.item"),
                        scope.displayName(),
                        scope.documentCount()
                ));
            }
        };
    }

    private void setError(String message) {
        boolean visible = message != null && !message.isBlank();
        errorLabel.setText(visible ? message : "");
        errorLabel.setVisible(visible);
        errorLabel.setManaged(visible);
    }
}
