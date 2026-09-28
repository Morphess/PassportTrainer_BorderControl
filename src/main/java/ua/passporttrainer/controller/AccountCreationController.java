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
import ua.passporttrainer.service.InitialAdministratorRequiredException;
import ua.passporttrainer.service.PasswordConfirmationException;
import ua.passporttrainer.service.UsernameAlreadyExistsException;

/** Creates ordinary local USER accounts without exposing role selection. */
public final class AccountCreationController {

    private final NavigationService navigationService;
    private final AccountSessionService accountSessionService;
    private final ResourceBundle resources;

    @FXML
    private TextField usernameField;
    @FXML
    private TextField displayNameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField passwordConfirmationField;
    @FXML
    private Label errorLabel;

    public AccountCreationController(
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
        Objects.requireNonNull(displayNameField, "displayNameField was not injected");
        Objects.requireNonNull(passwordField, "passwordField was not injected");
        Objects.requireNonNull(
                passwordConfirmationField, "passwordConfirmationField was not injected"
        );
        Objects.requireNonNull(errorLabel, "errorLabel was not injected");
        setError(null);
    }

    @FXML
    private void createAccount() {
        setError(null);
        char[] password = passwordField.getText().toCharArray();
        char[] confirmation = passwordConfirmationField.getText().toCharArray();
        try {
            accountSessionService.createOrdinaryAccountAndLogin(
                    usernameField.getText(),
                    displayNameField.getText(),
                    password,
                    confirmation
            );
            navigationService.resetTo(View.START);
        } catch (PasswordConfirmationException exception) {
            setError(resources.getString("account.error.passwordConfirmation"));
        } catch (UsernameAlreadyExistsException exception) {
            setError(resources.getString("account.error.usernameExists"));
        } catch (InitialAdministratorRequiredException exception) {
            setError(resources.getString("account.error.initialAdministratorRequired"));
        } catch (IllegalArgumentException exception) {
            setError(resources.getString("account.error.invalidFields"));
        } catch (RuntimeException exception) {
            setError(resources.getString("account.error.creationFailed"));
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            passwordField.clear();
            passwordConfirmationField.clear();
        }
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }

    private void setError(String message) {
        errorLabel.setText(message == null ? "" : message);
        errorLabel.setVisible(message != null);
        errorLabel.setManaged(message != null);
    }
}
