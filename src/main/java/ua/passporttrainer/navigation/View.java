package ua.passporttrainer.navigation;

/**
 * Registry of navigable FXML views.
 */
public enum View {
    START("/fxml/start-view.fxml"),
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
