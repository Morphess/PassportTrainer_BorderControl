package ua.passporttrainer.navigation;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The single application component responsible for resolving and loading FXML.
 */
public final class NavigationService {

    private static final String STYLESHEET = "/css/application.css";
    private static final double INITIAL_WIDTH = 1100;
    private static final double INITIAL_HEIGHT = 700;

    private final Stage stage;
    private final ResourceBundle resources;
    private final NavigationControllerFactory controllerFactory;
    private final Deque<Parent> history = new ArrayDeque<>();

    public NavigationService(
            Stage stage,
            ResourceBundle resources,
            NavigationControllerFactory controllerFactory
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.controllerFactory = Objects.requireNonNull(controllerFactory, "controllerFactory");
    }

    public void show(View view) {
        show(view, Object.class, ignored -> {
        });
    }

    public <T> void show(View view, Class<T> controllerType, Consumer<T> initializer) {
        setRoot(loadRoot(view, controllerType, initializer), true);
    }

    public void replace(View view) {
        replace(view, Object.class, ignored -> {
        });
    }

    public <T> void replace(View view, Class<T> controllerType, Consumer<T> initializer) {
        setRoot(loadRoot(view, controllerType, initializer), false);
    }

    public void resetTo(View view) {
        resetTo(view, Object.class, ignored -> {
        });
    }

    public <T> void resetTo(View view, Class<T> controllerType, Consumer<T> initializer) {
        Parent root = loadRoot(view, controllerType, initializer);
        history.clear();
        setRoot(root, false);
    }

    public boolean canGoBack() {
        return !history.isEmpty();
    }

    public void goBack() {
        if (!history.isEmpty()) {
            setRoot(history.pop(), false);
        }
    }

    private <T> Parent loadRoot(
            View view,
            Class<T> controllerType,
            Consumer<T> initializer
    ) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(controllerType, "controllerType");
        Objects.requireNonNull(initializer, "initializer");

        URL fxml = requireResource(view.fxmlPath());
        FXMLLoader loader = new FXMLLoader(fxml, resources);
        loader.setControllerFactory(type -> controllerFactory.create(type, this));

        try {
            Parent root = loader.load();
            Object controller = loader.getController();
            if (!controllerType.isInstance(controller)) {
                throw new NavigationException(
                        "Unexpected controller for " + view + ": " + controller.getClass().getName(),
                        null
                );
            }
            initializer.accept(controllerType.cast(controller));
            return root;
        } catch (IOException exception) {
            throw new NavigationException("Cannot load view: " + view, exception);
        }
    }

    private void setRoot(Parent root, boolean rememberCurrent) {
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
            scene.getStylesheets().add(requireResource(STYLESHEET).toExternalForm());
            stage.setScene(scene);
            return;
        }
        if (rememberCurrent) {
            history.push(scene.getRoot());
        }
        scene.setRoot(root);
    }

    private URL requireResource(String path) {
        URL resource = NavigationService.class.getResource(path);
        if (resource == null) {
            throw new NavigationException("Missing resource: " + path, null);
        }
        return resource;
    }
}
