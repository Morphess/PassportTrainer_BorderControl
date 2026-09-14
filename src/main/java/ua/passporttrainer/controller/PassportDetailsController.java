package ua.passporttrainer.controller;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import ua.passporttrainer.image.PassportImageLocator;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.service.PassportService;

import java.net.URI;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

/** Presentation controller for one document and its image sequence. */
public final class PassportDetailsController {

    private final NavigationService navigationService;
    private final PassportService passportService;
    private final PassportImageLocator imageLocator;
    private final ResourceBundle resources;

    private List<PassportImage> images = List.of();
    private int imageIndex = -1;

    @FXML
    private Label headingLabel;
    @FXML
    private Label metadataLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Label imageCounterLabel;
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

    public PassportDetailsController(
            NavigationService navigationService,
            PassportService passportService,
            PassportImageLocator imageLocator,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.passportService = Objects.requireNonNull(passportService, "passportService");
        this.imageLocator = Objects.requireNonNull(imageLocator, "imageLocator");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(headingLabel, "headingLabel was not injected");
        Objects.requireNonNull(metadataLabel, "metadataLabel was not injected");
        Objects.requireNonNull(errorLabel, "errorLabel was not injected");
        Objects.requireNonNull(imageCounterLabel, "imageCounterLabel was not injected");
        Objects.requireNonNull(imagePane, "imagePane was not injected");
        Objects.requireNonNull(passportImageView, "passportImageView was not injected");
        Objects.requireNonNull(loadingIndicator, "loadingIndicator was not injected");
        Objects.requireNonNull(previousButton, "previousButton was not injected");
        Objects.requireNonNull(nextButton, "nextButton was not injected");

        passportImageView.setPreserveRatio(true);
        passportImageView.setSmooth(true);
        passportImageView.fitWidthProperty().bind(Bindings.max(240.0, imagePane.widthProperty().subtract(48.0)));
        passportImageView.fitHeightProperty().bind(Bindings.max(280.0, imagePane.heightProperty().subtract(48.0)));
        setError(null);
        updateNavigationControls();
    }

    public void showDocument(String documentId) {
        try {
            PassportDocument document = passportService.findById(documentId).orElse(null);
            if (document == null) {
                showUnavailable("error.documentMissing");
                return;
            }
            headingLabel.setText(document.title());
            metadataLabel.setText(MessageFormat.format(
                    resources.getString("details.metadata"),
                    resources.getString("documentType." + document.documentType().name()),
                    resources.getString("reviewStatus." + document.reviewStatus().name())
            ));
            images = passportService.findImages(document.id());
            if (images.isEmpty()) {
                showUnavailable("error.imageMissing");
                return;
            }
            imageIndex = 0;
            showCurrentImage();
        } catch (RuntimeException exception) {
            showUnavailable("error.imagesLoad");
        }
    }

    @FXML
    private void showPreviousImage() {
        if (imageIndex > 0) {
            imageIndex--;
            showCurrentImage();
        }
    }

    @FXML
    private void showNextImage() {
        if (imageIndex >= 0 && imageIndex + 1 < images.size()) {
            imageIndex++;
            showCurrentImage();
        }
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }

    private void showCurrentImage() {
        PassportImage imageMetadata = images.get(imageIndex);
        setError(null);
        loadingIndicator.progressProperty().unbind();
        loadingIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        loadingIndicator.setVisible(true);
        passportImageView.setImage(null);
        updateNavigationControls();

        URI location;
        try {
            location = imageLocator.locate(imageMetadata.relativePath());
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
        images = List.of();
        imageIndex = -1;
        passportImageView.setImage(null);
        loadingIndicator.progressProperty().unbind();
        loadingIndicator.setVisible(false);
        headingLabel.setText(resources.getString("details.unavailableHeading"));
        metadataLabel.setText("");
        setError(resources.getString(errorKey));
        updateNavigationControls();
    }

    private void updateNavigationControls() {
        previousButton.setDisable(imageIndex <= 0);
        nextButton.setDisable(imageIndex < 0 || imageIndex + 1 >= images.size());
        imageCounterLabel.setText(imageIndex < 0
                ? ""
                : MessageFormat.format(resources.getString("details.imageCounter"), imageIndex + 1, images.size()));
    }

    private void setError(String message) {
        boolean visible = message != null && !message.isBlank();
        errorLabel.setText(visible ? message : "");
        errorLabel.setVisible(visible);
        errorLabel.setManaged(visible);
    }
}
