package ua.passporttrainer.controller;

import java.net.URI;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import ua.passporttrainer.image.PassportImageLocator;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.QuestionLayoutKind;
import ua.passporttrainer.presentation.QuestionOptionPresentation;
import ua.passporttrainer.presentation.QuestionPresentation;
import ua.passporttrainer.presentation.QuestionPresentationMapper;
import ua.passporttrainer.service.QuizAnswerSubmissionResult;
import ua.passporttrainer.service.QuizSessionCoordinator;
import ua.passporttrainer.service.QuizSessionProgress;

/** Presents and advances one coordinator-owned Training session without grading answers. */
public final class QuestionController {

    private final NavigationService navigationService;
    private final QuizSessionCoordinator sessionCoordinator;
    private final QuestionPresentationMapper presentationMapper;
    private final PassportImageLocator imageLocator;
    private final TrainingAbandonmentConfirmation abandonmentConfirmation;
    private final ResourceBundle resources;

    private final ToggleGroup optionGroup = new ToggleGroup();
    private final Map<String, ToggleButton> optionControls = new LinkedHashMap<>();
    private boolean answerSubmitted;
    private boolean completionReady;
    private boolean imageFailure;
    private int pendingImages;
    private long imageLoadVersion;

    @FXML
    private Label progressLabel;
    @FXML
    private Label promptLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Label subjectTextLabel;
    @FXML
    private StackPane subjectImagePane;
    @FXML
    private ImageView subjectImageView;
    @FXML
    private ProgressIndicator subjectLoadingIndicator;
    @FXML
    private VBox textOptionsBox;
    @FXML
    private GridPane imageOptionsGrid;
    @FXML
    private Button submitButton;
    @FXML
    private Button nextButton;

    public QuestionController(
            NavigationService navigationService,
            QuizSessionCoordinator sessionCoordinator,
            QuestionPresentationMapper presentationMapper,
            PassportImageLocator imageLocator,
            TrainingAbandonmentConfirmation abandonmentConfirmation,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.sessionCoordinator = Objects.requireNonNull(sessionCoordinator, "sessionCoordinator");
        this.presentationMapper = Objects.requireNonNull(presentationMapper, "presentationMapper");
        this.imageLocator = Objects.requireNonNull(imageLocator, "imageLocator");
        this.abandonmentConfirmation = Objects.requireNonNull(
                abandonmentConfirmation, "abandonmentConfirmation"
        );
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(progressLabel, "progressLabel was not injected");
        Objects.requireNonNull(promptLabel, "promptLabel was not injected");
        Objects.requireNonNull(feedbackLabel, "feedbackLabel was not injected");
        Objects.requireNonNull(errorLabel, "errorLabel was not injected");
        Objects.requireNonNull(subjectTextLabel, "subjectTextLabel was not injected");
        Objects.requireNonNull(subjectImagePane, "subjectImagePane was not injected");
        Objects.requireNonNull(subjectImageView, "subjectImageView was not injected");
        Objects.requireNonNull(subjectLoadingIndicator, "subjectLoadingIndicator was not injected");
        Objects.requireNonNull(textOptionsBox, "textOptionsBox was not injected");
        Objects.requireNonNull(imageOptionsGrid, "imageOptionsGrid was not injected");
        Objects.requireNonNull(submitButton, "submitButton was not injected");
        Objects.requireNonNull(nextButton, "nextButton was not injected");

        subjectImageView.setAccessibleText(resources.getString("question.imageSubjectAccessible"));
        optionGroup.selectedToggleProperty().addListener((observable, previous, current) -> updateSubmitState());
        showCurrentQuestion();
    }

    @FXML
    private void submitAnswer() {
        if (answerSubmitted || optionGroup.getSelectedToggle() == null || submitButton.isDisabled()) {
            return;
        }
        String selectedOptionId = (String) optionGroup.getSelectedToggle().getUserData();
        QuizAnswerSubmissionResult result;
        try {
            result = sessionCoordinator.submitAnswer(selectedOptionId);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            setError(resources.getString("training.answerRejected"));
            return;
        }
        if (!(result instanceof QuizAnswerSubmissionResult.Feedback feedback)) {
            throw new IllegalStateException("Training submission did not disclose mode-safe feedback");
        }

        answerSubmitted = true;
        completionReady = feedback.completionReady();
        optionControls.values().forEach(control -> control.setDisable(true));
        showFeedback(feedback);
        submitButton.setDisable(true);
        nextButton.setDisable(false);
    }

    @FXML
    private void showNextQuestion() {
        if (!answerSubmitted || nextButton.isDisabled()) {
            return;
        }
        if (completionReady) {
            sessionCoordinator.complete();
            navigationService.replace(View.TRAINING_RESULT);
            return;
        }
        sessionCoordinator.advance();
        showCurrentQuestion();
    }

    @FXML
    private void requestBack() {
        if (!abandonmentConfirmation.confirmAbandonment()) {
            return;
        }
        sessionCoordinator.abandon();
        navigationService.goBack();
    }

    private void showCurrentQuestion() {
        imageLoadVersion++;
        answerSubmitted = false;
        completionReady = false;
        imageFailure = false;
        pendingImages = 0;
        optionControls.clear();
        optionGroup.getToggles().clear();
        textOptionsBox.getChildren().clear();
        imageOptionsGrid.getChildren().clear();
        subjectImageView.setImage(null);
        subjectLoadingIndicator.progressProperty().unbind();
        subjectLoadingIndicator.setVisible(false);
        hide(feedbackLabel);
        feedbackLabel.getStyleClass().removeAll("feedback-correct", "feedback-incorrect");
        setError(null);
        submitButton.setDisable(true);
        nextButton.setDisable(true);

        QuestionPresentation presentation = presentationMapper.map(sessionCoordinator.currentQuestion());
        QuizSessionProgress progress = sessionCoordinator.progress();
        progressLabel.setText(MessageFormat.format(
                resources.getString("training.progress"),
                progress.currentQuestionNumber(),
                progress.totalQuestions()
        ));
        promptLabel.setText(presentation.prompt());
        nextButton.setText(resources.getString(
                progress.currentQuestionNumber() == progress.totalQuestions()
                        ? "training.finish"
                        : "training.next"
        ));

        if (presentation.layout() == QuestionLayoutKind.IMAGE_SUBJECT_TEXT_OPTIONS) {
            showImageSubjectQuestion(presentation);
        } else {
            showImageOptionQuestion(presentation);
        }
        updateSubmitState();
    }

    private void showImageSubjectQuestion(QuestionPresentation presentation) {
        setVisible(subjectTextLabel, false);
        setVisible(subjectImagePane, true);
        setVisible(textOptionsBox, true);
        setVisible(imageOptionsGrid, false);
        loadImage(
                subjectImageView,
                subjectLoadingIndicator,
                presentation.subject().imageRelativePath(),
                imageLoadVersion
        );

        for (QuestionOptionPresentation option : presentation.options()) {
            ToggleButton control = new ToggleButton(option.label());
            configureOptionControl(control, option);
            control.setMaxWidth(Double.MAX_VALUE);
            control.setWrapText(true);
            textOptionsBox.getChildren().add(control);
        }
    }

    private void showImageOptionQuestion(QuestionPresentation presentation) {
        subjectTextLabel.setText(presentation.subject().text());
        setVisible(subjectTextLabel, true);
        setVisible(subjectImagePane, false);
        setVisible(textOptionsBox, false);
        setVisible(imageOptionsGrid, true);

        for (int index = 0; index < presentation.options().size(); index++) {
            QuestionOptionPresentation option = presentation.options().get(index);
            ToggleButton control = new ToggleButton();
            configureOptionControl(control, option);
            control.getStyleClass().add("image-question-option");

            ImageView imageView = new ImageView();
            imageView.setFitWidth(190.0);
            imageView.setFitHeight(135.0);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setAccessibleText(option.label());
            ProgressIndicator loading = new ProgressIndicator();
            loading.setMaxSize(40.0, 40.0);
            StackPane imagePane = new StackPane(imageView, loading);
            imagePane.getStyleClass().add("question-option-image");
            Label neutralLabel = new Label(option.label());
            neutralLabel.getStyleClass().add("image-option-label");
            VBox card = new VBox(5.0, imagePane, neutralLabel);
            card.setAlignment(Pos.CENTER);
            control.setGraphic(card);
            control.setText("");
            imageOptionsGrid.add(control, index % 2, index / 2);
            loadImage(imageView, loading, option.imageRelativePath(), imageLoadVersion);
        }
    }

    private void configureOptionControl(
            ToggleButton control,
            QuestionOptionPresentation option
    ) {
        control.setToggleGroup(optionGroup);
        control.setUserData(option.optionId());
        control.setAccessibleText(option.label());
        control.getStyleClass().add("question-option");
        optionControls.put(option.optionId(), control);
    }

    private void loadImage(
            ImageView imageView,
            ProgressIndicator loadingIndicator,
            String imageRelativePath,
            long version
    ) {
        pendingImages++;
        loadingIndicator.progressProperty().unbind();
        loadingIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        loadingIndicator.setVisible(true);

        URI location;
        try {
            location = imageLocator.locate(imageRelativePath);
        } catch (RuntimeException failure) {
            settleImage(version, loadingIndicator, true);
            return;
        }

        Image image = new Image(location.toString(), true);
        imageView.setImage(image);
        loadingIndicator.progressProperty().bind(image.progressProperty());
        boolean[] settled = {false};
        image.errorProperty().addListener((observable, previous, failed) -> {
            if (failed && !settled[0]) {
                settled[0] = true;
                imageView.setImage(null);
                settleImage(version, loadingIndicator, true);
            }
        });
        image.progressProperty().addListener((observable, previous, progress) -> {
            if (progress.doubleValue() >= 1.0 && !settled[0]) {
                settled[0] = true;
                settleImage(version, loadingIndicator, image.isError());
            }
        });
        if (image.isError() && !settled[0]) {
            settled[0] = true;
            imageView.setImage(null);
            settleImage(version, loadingIndicator, true);
        } else if ((!image.isBackgroundLoading() || image.getProgress() >= 1.0) && !settled[0]) {
            settled[0] = true;
            settleImage(version, loadingIndicator, image.isError());
        }
    }

    private void settleImage(
            long version,
            ProgressIndicator loadingIndicator,
            boolean failed
    ) {
        if (version != imageLoadVersion) {
            return;
        }
        loadingIndicator.progressProperty().unbind();
        loadingIndicator.setVisible(false);
        pendingImages--;
        if (failed) {
            imageFailure = true;
            setError(resources.getString("training.imageUnavailable"));
        }
        updateSubmitState();
    }

    private void showFeedback(QuizAnswerSubmissionResult.Feedback feedback) {
        feedbackLabel.setText(resources.getString(
                feedback.correct() ? "training.correct" : "training.incorrect"
        ));
        feedbackLabel.getStyleClass().add(
                feedback.correct() ? "feedback-correct" : "feedback-incorrect"
        );
        setVisible(feedbackLabel, true);

        ToggleButton accepted = optionControls.get(feedback.acceptedAnswerId());
        ToggleButton correct = optionControls.get(feedback.correctOptionId());
        if (accepted == null || correct == null) {
            throw new IllegalStateException("Coordinator feedback references an unknown option");
        }
        accepted.getStyleClass().add("selected-answer");
        correct.getStyleClass().add("correct-answer");
        if (!feedback.correct()) {
            accepted.getStyleClass().add("incorrect-answer");
        }
    }

    private void updateSubmitState() {
        submitButton.setDisable(
                answerSubmitted
                        || optionGroup.getSelectedToggle() == null
                        || pendingImages > 0
                        || imageFailure
        );
    }

    private void setError(String message) {
        boolean visible = message != null && !message.isBlank();
        errorLabel.setText(visible ? message : "");
        setVisible(errorLabel, visible);
    }

    private static void hide(Label label) {
        label.setText("");
        setVisible(label, false);
    }

    private static void setVisible(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
