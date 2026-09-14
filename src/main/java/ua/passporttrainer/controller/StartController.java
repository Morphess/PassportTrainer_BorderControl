package ua.passporttrainer.controller;

import java.util.Objects;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;

/**
 * Controller for the initial status view only.
 */
public final class StartController {

    private final NavigationService navigationService;
    private final boolean catalogueAvailable;
    private final ResourceBundle resources;

    @FXML
    private Label titleLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button catalogueButton;

    public StartController(
            NavigationService navigationService,
            boolean catalogueAvailable,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.catalogueAvailable = catalogueAvailable;
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(titleLabel, "titleLabel was not injected");
        Objects.requireNonNull(statusLabel, "statusLabel was not injected");
        Objects.requireNonNull(catalogueButton, "catalogueButton was not injected");
        catalogueButton.setDisable(!catalogueAvailable);
        statusLabel.setText(resources.getString(
                catalogueAvailable ? "start.catalogueReady" : "error.catalogueUnavailable"
        ));
        if (!catalogueAvailable) {
            statusLabel.getStyleClass().add("error-label");
        }
    }

    @FXML
    private void openCatalogue() {
        if (catalogueAvailable) {
            navigationService.show(View.CATALOGUE);
        }
    }
}
