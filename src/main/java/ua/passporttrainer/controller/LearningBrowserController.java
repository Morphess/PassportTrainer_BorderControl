package ua.passporttrainer.controller;

import java.net.URI;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import ua.passporttrainer.image.PassportImageLocator;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.LearningDocumentPresentation;
import ua.passporttrainer.service.LearningService;

/** Presents reviewed Learning documents as passive, non-scored study material. */
public final class LearningBrowserController {

    private final NavigationService navigationService;
    private final LearningService learningService;
    private final PassportImageLocator imageLocator;
    private final ResourceBundle resources;

    private List<LearningDocumentPresentation> documents = List.of();
    private int documentIndex = -1;

    @FXML
    private Label headingLabel;
    @FXML
    private Label documentTypeLabel;
    @FXML
    private Label documentTitleLabel;
    @FXML
    private Label variantLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Label documentCounterLabel;
    @FXML
    private StackPane imagePane;
    @FXML
    private ImageView passportImageView;
    @FXML
    private ProgressIndicator loadingIndicator;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;
    @FXML
    private Button practiceButton;

    public LearningBrowserController(
            NavigationService navigationService,
            LearningService learningService,
            PassportImageLocator imageLocator,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.learningService = Objects.requireNonNull(learningService, "learningService");
        this.imageLocator = Objects.requireNonNull(imageLocator, "imageLocator");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(headingLabel, "headingLabel was not injected");
        Objects.requireNonNull(documentTypeLabel, "documentTypeLabel was not injected");
        Objects.requireNonNull(documentTitleLabel, "documentTitleLabel was not injected");
        Objects.requireNonNull(variantLabel, "variantLabel was not injected");
        Objects.requireNonNull(errorLabel, "errorLabel was not injected");
        Objects.requireNonNull(documentCounterLabel, "documentCounterLabel was not injected");
        Objects.requireNonNull(imagePane, "imagePane was not injected");
        Objects.requireNonNull(passportImageView, "passportImageView was not injected");
        Objects.requireNonNull(loadingIndicator, "loadingIndicator was not injected");
        Objects.requireNonNull(previousButton, "previousButton was not injected");
        Objects.requireNonNull(nextButton, "nextButton was not injected");
        Objects.requireNonNull(practiceButton, "practiceButton was not injected");

        passportImageView.setPreserveRatio(true);
        passportImageView.setSmooth(true);
        passportImageView.fitWidthProperty().bind(Bindings.max(240.0, imagePane.widthProperty().subtract(48.0)));
        passportImageView.fitHeightProperty().bind(Bindings.max(280.0, imagePane.heightProperty().subtract(48.0)));
        setError(null);
        updateNavigationControls();
    }

    public void showEntity(String entityId) {
        try {
            documents = learningService.findEligibleDocuments(entityId);
            if (documents.isEmpty()) {
                showUnavailable("learning.entityUnavailable");
                return;
            }
            documentIndex = 0;
            showCurrentDocument();
        } catch (RuntimeException exception) {
            showUnavailable("error.learningLoad");
        }
    }

    @FXML
    private void showPreviousDocument() {
        if (documentIndex > 0) {
            documentIndex--;
            showCurrentDocument();
        }
    }

    @FXML
    private void showNextDocument() {
        if (documentIndex >= 0 && documentIndex + 1 < documents.size()) {
            documentIndex++;
            showCurrentDocument();
        }
    }

    @FXML
    private void practiceCurrentEntity() {
        if (documentIndex < 0 || documentIndex >= documents.size()) {
            return;
        }
        String entityId = documents.get(documentIndex).entityId();
        navigationService.show(
                View.TRAINING_CONFIGURATION,
                TrainingConfigurationController.class,
                controller -> controller.showForEntity(entityId)
        );
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }

    private void showCurrentDocument() {
        LearningDocumentPresentation document = documents.get(documentIndex);
        String documentType = resources.getString(document.documentTypeLabelKey());
        headingLabel.setText(document.entityDisplayName());
        documentTypeLabel.setText(documentType);
        boolean meaningfulTitle = !document.title().equalsIgnoreCase(documentType);
        setOptionalText(documentTitleLabel, meaningfulTitle ? document.title() : "");
        setOptionalText(variantLabel, document.hasMeaningfulVariant()
                ? MessageFormat.format(resources.getString("learning.variant"), document.variant())
                : "");
        documentCounterLabel.setText(MessageFormat.format(
                resources.getString("learning.documentCounter"), documentIndex + 1, documents.size()
        ));
        passportImageView.setAccessibleText(MessageFormat.format(
                resources.getString("learning.imageAccessible"),
                document.entityDisplayName(),
                documentType,
                document.title()
        ));
        setError(null);
        updateNavigationControls();
        loadImage(document);
    }

    private void loadImage(LearningDocumentPresentation document) {
        loadingIndicator.progressProperty().unbind();
        loadingIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        loadingIndicator.setVisible(true);
        passportImageView.setImage(null);

        URI location;
        try {
            location = imageLocator.locate(document.imageRelativePath());
        } catch (RuntimeException exception) {
            loadingIndicator.setVisible(false);
            setError(resources.getString("error.imageMissing"));
            return;
        }

        Image image = new Image(location.toString(), true);
        passportImageView.setImage(image);
        loadingIndicator.progressProperty().bind(image.progressProperty());
        image.progressProperty().addListener((observable, previous, current) -> {
            if (passportImageView.getImage() == image && current.doubleValue() >= 1.0) {
                loadingIndicator.progressProperty().unbind();
                loadingIndicator.setVisible(false);
            }
        });
        image.errorProperty().addListener((observable, previous, failed) -> {
            if (failed) {
                showImageFailure(image);
            }
        });
        if (image.isError()) {
            showImageFailure(image);
        } else if (!image.isBackgroundLoading() || image.getProgress() >= 1.0) {
            loadingIndicator.progressProperty().unbind();
            loadingIndicator.setVisible(false);
        }
    }

    private void showImageFailure(Image image) {
        if (passportImageView.getImage() != image) {
            return;
        }
        loadingIndicator.progressProperty().unbind();
        loadingIndicator.setVisible(false);
        passportImageView.setImage(null);
        setError(resources.getString("error.imageUnreadable"));
    }

    private void showUnavailable(String errorKey) {
        documents = List.of();
        documentIndex = -1;
        passportImageView.setImage(null);
        passportImageView.setAccessibleText(resources.getString("learning.imageUnavailableAccessible"));
        loadingIndicator.progressProperty().unbind();
        loadingIndicator.setVisible(false);
        headingLabel.setText(resources.getString("learning.unavailableHeading"));
        documentTypeLabel.setText("");
        setOptionalText(documentTitleLabel, "");
        setOptionalText(variantLabel, "");
        documentCounterLabel.setText("");
        setError(resources.getString(errorKey));
        updateNavigationControls();
    }

    private void updateNavigationControls() {
        previousButton.setDisable(documentIndex <= 0);
        nextButton.setDisable(documentIndex < 0 || documentIndex + 1 >= documents.size());
        practiceButton.setDisable(documentIndex < 0 || documentIndex >= documents.size());
    }

    private static void setOptionalText(Label label, String text) {
        boolean visible = text != null && !text.isBlank();
        label.setText(visible ? text : "");
        label.setVisible(visible);
        label.setManaged(visible);
    }

    private void setError(String message) {
        boolean visible = message != null && !message.isBlank();
        errorLabel.setText(visible ? message : "");
        errorLabel.setVisible(visible);
        errorLabel.setManaged(visible);
    }
}
