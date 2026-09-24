package ua.passporttrainer.controller;

import java.util.Objects;
import java.util.ResourceBundle;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/** JavaFX confirmation dialog used before discarding active Training progress. */
public final class JavaFxTrainingAbandonmentConfirmation implements TrainingAbandonmentConfirmation {

    private final ResourceBundle resources;

    public JavaFxTrainingAbandonmentConfirmation(ResourceBundle resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @Override
    public boolean confirmAbandonment() {
        ButtonType abandon = new ButtonType(
                resources.getString("training.abandonConfirm"),
                ButtonBar.ButtonData.OK_DONE
        );
        ButtonType cancel = new ButtonType(
                resources.getString("training.abandonCancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE
        );
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(resources.getString("training.abandonTitle"));
        alert.setHeaderText(resources.getString("training.abandonHeader"));
        alert.setContentText(resources.getString("training.abandonContent"));
        alert.getButtonTypes().setAll(abandon, cancel);
        return alert.showAndWait().filter(abandon::equals).isPresent();
    }
}
