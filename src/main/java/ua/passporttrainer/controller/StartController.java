package ua.passporttrainer.controller;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.security.AuthenticatedUser;
import ua.passporttrainer.service.AccountSessionService;

/** Controller for the main application entry surface. */
public final class StartController {

    private final NavigationService navigationService;
    private final boolean catalogueAvailable;
    private final boolean mapAvailable;
    private final ResourceBundle resources;
    private final Optional<AccountSessionService> accountSessionService;

    @FXML
    private Label titleLabel;

    @FXML
    private Label statusLabel;
    @FXML
    private Label mapStatusLabel;
    @FXML
    private Label currentUserLabel;


    @FXML
    private Button catalogueButton;
    @FXML
    private Button mapButton;
    @FXML
    private Button modeSelectionButton;
    @FXML
    private Button logoutButton;
    @FXML
    private Button switchUserButton;


    public StartController(
            NavigationService navigationService,
            boolean catalogueAvailable,
            boolean mapAvailable,
            ResourceBundle resources,
            Optional<AccountSessionService> accountSessionService
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.catalogueAvailable = catalogueAvailable;
        this.mapAvailable = mapAvailable;
        this.resources = Objects.requireNonNull(resources, "resources");
        this.accountSessionService = Objects.requireNonNull(
                accountSessionService, "accountSessionService"
        );
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(titleLabel, "titleLabel was not injected");
        Objects.requireNonNull(statusLabel, "statusLabel was not injected");
        Objects.requireNonNull(mapStatusLabel, "mapStatusLabel was not injected");
        Objects.requireNonNull(catalogueButton, "catalogueButton was not injected");
        Objects.requireNonNull(modeSelectionButton, "modeSelectionButton was not injected");
        Objects.requireNonNull(mapButton, "mapButton was not injected");
        Objects.requireNonNull(currentUserLabel, "currentUserLabel was not injected");
        Objects.requireNonNull(logoutButton, "logoutButton was not injected");
        Objects.requireNonNull(switchUserButton, "switchUserButton was not injected");
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
        showCurrentUser();
    }
    @FXML
    private void logout() {
        accountSessionService.ifPresent(service -> {
            service.logout();
            navigationService.resetTo(View.LOGIN);
        });
    }

    @FXML
    private void switchUser() {
        accountSessionService.ifPresent(service -> {
            service.switchUser();
            navigationService.resetTo(View.LOGIN);
        });
    }

    private void showCurrentUser() {
        if (accountSessionService.isEmpty()) {
            currentUserLabel.setText(resources.getString("account.currentUnavailable"));
            logoutButton.setDisable(true);
            switchUserButton.setDisable(true);
            return;
        }
        Optional<AuthenticatedUser> current = accountSessionService.orElseThrow().currentUser();
        if (current.isEmpty()) {
            currentUserLabel.setText(resources.getString("account.currentAnonymous"));
            logoutButton.setDisable(true);
            return;
        }
        AuthenticatedUser user = current.orElseThrow();
        currentUserLabel.setText(MessageFormat.format(
                resources.getString("account.currentUser"),
                user.displayName(),
                resources.getString("account.role." + user.role().name())
        ));
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
