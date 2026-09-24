package ua.passporttrainer.controller;

import java.util.Objects;
import java.util.ResourceBundle;

import ua.passporttrainer.model.QuizMode;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/** JavaFX confirmation dialog used before discarding active quiz progress. */
public final class JavaFxSessionAbandonmentConfirmation implements SessionAbandonmentConfirmation {

    private final ResourceBundle resources;

    public JavaFxSessionAbandonmentConfirmation(ResourceBundle resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @Override
    public boolean confirmAbandonment(QuizMode mode) {
        Objects.requireNonNull(mode, "mode");
        String keyPrefix = switch (mode) {
            case TRAINING -> "training";
            case EXAMINATION -> "examination";
            case LEARNING -> throw new IllegalArgumentException(
                    "Learning does not own a quiz session"
            );
        };
        ButtonType abandon = new ButtonType(
                resources.getString(keyPrefix + ".abandonConfirm"),
                ButtonBar.ButtonData.OK_DONE
        );
        ButtonType cancel = new ButtonType(
                resources.getString(keyPrefix + ".abandonCancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE
        );
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(resources.getString(keyPrefix + ".abandonTitle"));
        alert.setHeaderText(resources.getString(keyPrefix + ".abandonHeader"));
        alert.setContentText(resources.getString(keyPrefix + ".abandonContent"));
        alert.getButtonTypes().setAll(abandon, cancel);
        return alert.showAndWait().filter(abandon::equals).isPresent();
    }
}
