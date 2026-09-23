package ua.passporttrainer.controller;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.LearningEntityPresentation;
import ua.passporttrainer.service.LearningService;

/** Selects an entity exclusively from the reviewed Learning pool. */
public final class LearningEntitySelectionController {

    private final NavigationService navigationService;
    private final LearningService learningService;
    private final ResourceBundle resources;

    @FXML
    private ListView<LearningEntityPresentation> entityList;
    @FXML
    private Label messageLabel;
    @FXML
    private Button openButton;

    public LearningEntitySelectionController(
            NavigationService navigationService,
            LearningService learningService,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.learningService = Objects.requireNonNull(learningService, "learningService");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(entityList, "entityList was not injected");
        Objects.requireNonNull(messageLabel, "messageLabel was not injected");
        Objects.requireNonNull(openButton, "openButton was not injected");

        entityList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(LearningEntityPresentation entity, boolean empty) {
                super.updateItem(entity, empty);
                setText(empty || entity == null ? null : MessageFormat.format(
                        resources.getString("learning.entityItem"),
                        entity.displayName(),
                        entity.documentCount()
                ));
            }
        });
        openButton.disableProperty().bind(entityList.getSelectionModel().selectedItemProperty().isNull());
        entityList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedEntity();
            }
        });
        loadEntities();
    }

    @FXML
    private void openSelectedEntity() {
        LearningEntityPresentation selected = entityList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            navigationService.show(
                    View.LEARNING_BROWSER,
                    LearningBrowserController.class,
                    controller -> controller.showEntity(selected.entityId())
            );
        }
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }

    private void loadEntities() {
        try {
            List<LearningEntityPresentation> entities = learningService.findEligibleEntities();
            entityList.getItems().setAll(entities);
            if (entities.isEmpty()) {
                showMessage("learning.emptyEntities", false);
            } else {
                entityList.getSelectionModel().selectFirst();
                showMessage("learning.selectionNotice", false);
            }
        } catch (RuntimeException exception) {
            entityList.getItems().clear();
            showMessage("error.learningLoad", true);
        }
    }

    private void showMessage(String key, boolean error) {
        messageLabel.setText(resources.getString(key));
        messageLabel.getStyleClass().remove("error-label");
        if (error) {
            messageLabel.getStyleClass().add("error-label");
        }
    }
}
