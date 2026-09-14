package ua.passporttrainer.navigation;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The single application component responsible for resolving and loading FXML.
 */
public final class NavigationService {

    private static final String STYLESHEET = "/css/application.css";
    private static final double INITIAL_WIDTH = 720;
    private static final double INITIAL_HEIGHT = 420;

    private final Stage stage;
    private final ResourceBundle resources;

    public NavigationService(Stage stage, ResourceBundle resources) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public void show(View view) {
        Objects.requireNonNull(view, "view");

        URL fxml = requireResource(view.fxmlPath());
        FXMLLoader loader = new FXMLLoader(fxml, resources);

        try {
            setRoot(loader.load());
        } catch (IOException exception) {
            throw new NavigationException("Cannot load view: " + view, exception);
        }
    }

    private void setRoot(Parent root) {
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
            scene.getStylesheets().add(requireResource(STYLESHEET).toExternalForm());
            stage.setScene(scene);
            return;
        }
        scene.setRoot(root);
    }

    private URL requireResource(String path) {
        return Objects.requireNonNull(
                NavigationService.class.getResource(path),
                "Missing resource: " + path
        );
    }
}
