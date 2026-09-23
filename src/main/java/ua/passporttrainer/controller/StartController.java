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
    private final boolean mapAvailable;
    private final ResourceBundle resources;

    @FXML
    private Label titleLabel;

    @FXML
    private Label statusLabel;
    @FXML
    private Label mapStatusLabel;


    @FXML
    private Button catalogueButton;
    @FXML
    private Button mapButton;
    @FXML
    private Button modeSelectionButton;


    public StartController(
            NavigationService navigationService,
            boolean catalogueAvailable,
            boolean mapAvailable,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.catalogueAvailable = catalogueAvailable;
        this.mapAvailable = mapAvailable;
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(titleLabel, "titleLabel was not injected");
        Objects.requireNonNull(statusLabel, "statusLabel was not injected");
        Objects.requireNonNull(mapStatusLabel, "mapStatusLabel was not injected");
        Objects.requireNonNull(catalogueButton, "catalogueButton was not injected");
        Objects.requireNonNull(modeSelectionButton, "modeSelectionButton was not injected");
        Objects.requireNonNull(mapButton, "mapButton was not injected");
        modeSelectionButton.setDisable(!catalogueAvailable);
        catalogueButton.setDisable(!catalogueAvailable);
        mapButton.setDisable(!mapAvailable);
        statusLabel.setText(resources.getString(
                catalogueAvailable ? "start.catalogueReady" : "error.catalogueUnavailable"
        ));
        mapStatusLabel.setText(resources.getString(
                mapAvailable ? "start.mapReady" : "error.mapUnavailable"
        ));
        if (!catalogueAvailable) {
            statusLabel.getStyleClass().add("error-label");
        }
        if (!mapAvailable) {
            mapStatusLabel.getStyleClass().add("error-label");
        }
    }
    @FXML
    private void openModeSelection() {
        if (catalogueAvailable) {
            navigationService.show(View.MODE_SELECTION);
        }
    }

    @FXML
    private void openWorldMap() {
        if (mapAvailable) {
            navigationService.show(View.WORLD_MAP);
        }
    }


    @FXML
    private void openCatalogue() {
        if (catalogueAvailable) {
            navigationService.show(View.CATALOGUE);
        }
    }
}
