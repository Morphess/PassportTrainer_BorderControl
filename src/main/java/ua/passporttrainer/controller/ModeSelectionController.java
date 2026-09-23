package ua.passporttrainer.controller;

import java.util.Objects;

import javafx.fxml.FXML;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;

/** Navigation-only controller for choosing an available product mode. */
public final class ModeSelectionController {

    private final NavigationService navigationService;

    public ModeSelectionController(NavigationService navigationService) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
    }

    @FXML
    private void openLearning() {
        navigationService.show(View.LEARNING_ENTITIES);
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }
}
