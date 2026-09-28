package ua.passporttrainer.controller;

import java.util.Arrays;
import java.util.Objects;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.service.AccountSessionService;

/** Handles generic local authentication and explicit anonymous entry. */
public final class LoginController {

    private final NavigationService navigationService;
    private final AccountSessionService accountSessionService;
    private final ResourceBundle resources;

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    public LoginController(
            NavigationService navigationService,
            AccountSessionService accountSessionService,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.accountSessionService = Objects.requireNonNull(
                accountSessionService, "accountSessionService"
        );
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(usernameField, "usernameField was not injected");
        Objects.requireNonNull(passwordField, "passwordField was not injected");
        Objects.requireNonNull(errorLabel, "errorLabel was not injected");
        setError(null);
    }

    @FXML
    private void login() {
        setError(null);
        char[] password = passwordField.getText().toCharArray();
        try {
            if (!accountSessionService.login(usernameField.getText(), password)) {
                setError(resources.getString("login.error.invalidCredentials"));
                return;
            }
            navigationService.resetTo(View.START);
        } catch (RuntimeException exception) {
            setError(resources.getString("login.error.invalidCredentials"));
        } finally {
            Arrays.fill(password, '\0');
            passwordField.clear();
        }
    }

    @FXML
    private void continueAnonymously() {
        accountSessionService.continueAnonymously();
        navigationService.resetTo(View.START);
    }

    @FXML
    private void createAccount() {
        navigationService.show(View.ACCOUNT_CREATION);
    }

    private void setError(String message) {
        errorLabel.setText(message == null ? "" : message);
        errorLabel.setVisible(message != null);
        errorLabel.setManaged(message != null);
    }
}
