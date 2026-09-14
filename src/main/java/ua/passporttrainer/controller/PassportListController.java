package ua.passporttrainer.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.service.CountryService;
import ua.passporttrainer.service.PassportService;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

/** Presentation controller for the documents of one canonical issuing entity. */
public final class PassportListController {

    private final NavigationService navigationService;
    private final CountryService countryService;
    private final PassportService passportService;
    private final ResourceBundle resources;

    @FXML
    private Label headingLabel;
    @FXML
    private Label messageLabel;
    @FXML
    private ListView<PassportDocument> documentList;
    @FXML
    private Button openButton;

    public PassportListController(
            NavigationService navigationService,
            CountryService countryService,
            PassportService passportService,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.countryService = Objects.requireNonNull(countryService, "countryService");
        this.passportService = Objects.requireNonNull(passportService, "passportService");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(headingLabel, "headingLabel was not injected");
        Objects.requireNonNull(messageLabel, "messageLabel was not injected");
        Objects.requireNonNull(documentList, "documentList was not injected");
        Objects.requireNonNull(openButton, "openButton was not injected");

        documentList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(PassportDocument document, boolean empty) {
                super.updateItem(document, empty);
                setText(empty || document == null ? null : documentText(document));
            }
        });
        openButton.disableProperty().bind(documentList.getSelectionModel().selectedItemProperty().isNull());
        documentList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedDocument();
            }
        });
    }

    public void showCountry(String countryId) {
        try {
            Country country = countryService.findById(countryId).orElse(null);
            if (country == null) {
                showFailure("error.entityMissing");
                return;
            }
            headingLabel.setText(MessageFormat.format(resources.getString("passports.heading"), country.displayName()));
            List<PassportDocument> documents = passportService.findByCountryId(country.id());
            documentList.getItems().setAll(documents);
            if (documents.isEmpty()) {
                showMessage("passports.empty");
            } else {
                showMessage("passports.provisionalNotice");
                documentList.getSelectionModel().selectFirst();
            }
        } catch (RuntimeException exception) {
            showFailure("error.documentsLoad");
        }
    }

    @FXML
    private void openSelectedDocument() {
        PassportDocument selected = documentList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            navigationService.show(
                    View.PASSPORT_DETAILS,
                    PassportDetailsController.class,
                    controller -> controller.showDocument(selected.id())
            );
        }
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }

    private String documentText(PassportDocument document) {
        return MessageFormat.format(
                resources.getString("passports.documentItem"),
                document.title(),
                resources.getString("documentType." + document.documentType().name()),
                resources.getString("reviewStatus." + document.reviewStatus().name()),
                document.imageCount()
        );
    }

    private void showMessage(String key) {
        messageLabel.setText(resources.getString(key));
        messageLabel.getStyleClass().remove("error-label");
    }

    private void showFailure(String key) {
        documentList.getItems().clear();
        headingLabel.setText(resources.getString("passports.unavailableHeading"));
        messageLabel.setText(resources.getString(key));
        if (!messageLabel.getStyleClass().contains("error-label")) {
            messageLabel.getStyleClass().add("error-label");
        }
    }
}
