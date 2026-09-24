package ua.passporttrainer.navigation;

/**
 * Registry of navigable FXML views.
 */
public enum View {
    START("/fxml/start-view.fxml"),
    MODE_SELECTION("/fxml/mode-selection-view.fxml"),
    LEARNING_ENTITIES("/fxml/learning-entity-selection-view.fxml"),
    LEARNING_BROWSER("/fxml/learning-browser-view.fxml"),
    TRAINING_CONFIGURATION("/fxml/training-configuration-view.fxml"),
    QUESTION("/fxml/question-view.fxml"),
    TRAINING_RESULT("/fxml/training-result-view.fxml"),
    CATALOGUE("/fxml/catalogue-view.fxml"),
    WORLD_MAP("/fxml/world-map-view.fxml"),
    PASSPORTS("/fxml/passport-list-view.fxml"),
    PASSPORT_DETAILS("/fxml/passport-details-view.fxml");

    private final String fxmlPath;

    View(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String fxmlPath() {
        return fxmlPath;
    }
}
