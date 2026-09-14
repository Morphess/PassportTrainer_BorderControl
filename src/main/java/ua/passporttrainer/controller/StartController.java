package ua.passporttrainer.controller;

import java.util.Objects;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for the initial status view only.
 */
public final class StartController {

    @FXML
    private Label titleLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        Objects.requireNonNull(titleLabel, "titleLabel was not injected");
        Objects.requireNonNull(statusLabel, "statusLabel was not injected");
    }
}
