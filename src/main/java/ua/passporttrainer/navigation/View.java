package ua.passporttrainer.navigation;

/**
 * Registry of navigable FXML views.
 */
public enum View {
    START("/fxml/start-view.fxml");

    private final String fxmlPath;

    View(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String fxmlPath() {
        return fxmlPath;
    }
}
