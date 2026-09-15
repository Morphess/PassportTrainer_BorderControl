package ua.passporttrainer.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.CatalogueTestSupport;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.controller.CatalogueControllerFactory;
import ua.passporttrainer.controller.PassportDetailsController;
import ua.passporttrainer.controller.PassportListController;
import ua.passporttrainer.image.FileSystemPassportImageLocator;
import ua.passporttrainer.map.WorldMapPane;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.repository.PassportRepository;
import ua.passporttrainer.service.PassportService;

import java.text.Normalizer;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogueUiSmokeTest {

    private static final ResourceBundle RESOURCES = ResourceBundle.getBundle("i18n.messages");
    private static final Pattern SVG_NUMBER = Pattern.compile(
            "-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"
    );

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start");
        Platform.setImplicitExit(false);
    }

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }

    @Test
    void opensCatalogueDocumentsAndImageDetailsThenNavigatesBack() throws Exception {
        Path database = temporaryDirectory.resolve("catalogue-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        CountDownLatch imageFinished = new CountDownLatch(1);
        AtomicReference<Throwable> imageFailure = new AtomicReference<>();
        AtomicReference<OpenFlow> flowReference = new AtomicReference<>();

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                CatalogueControllerFactory factory = CatalogueControllerFactory.available(
                        context,
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(View.START);
                stage.show();

                Parent startRoot = stage.getScene().getRoot();
                Button catalogueButton = control(startRoot, "#catalogueButton", Button.class);
                assertFalse(catalogueButton.isDisabled());
                catalogueButton.fire();

                Parent catalogueRoot = stage.getScene().getRoot();
                ListView<?> continents = control(catalogueRoot, "#continentList", ListView.class);
                ListView<?> entities = control(catalogueRoot, "#entityList", ListView.class);
                assertEquals(7, continents.getItems().size());
                TextField search = control(catalogueRoot, "#searchField", TextField.class);
                search.setText("уКрАїНа");
                assertEquals(1, entities.getItems().size());
                assertEquals("Україна", ((Country) entities.getItems().getFirst()).displayName());
                search.clear();
                assertFalse(entities.getItems().isEmpty());
                entities.getSelectionModel().selectFirst();
                control(catalogueRoot, "#openButton", Button.class).fire();

                Parent passportRoot = stage.getScene().getRoot();
                ListView<?> documents = control(passportRoot, "#documentList", ListView.class);
                assertFalse(documents.getItems().isEmpty());
                control(passportRoot, "#openButton", Button.class).fire();

                Parent detailsRoot = stage.getScene().getRoot();
                ImageView imageView = control(detailsRoot, "#passportImageView", ImageView.class);
                Image image = imageView.getImage();
                assertNotNull(image);
                if (image.isError()) {
                    imageFailure.set(image.getException());
                    imageFinished.countDown();
                } else if (image.getProgress() >= 1.0) {
                    imageFinished.countDown();
                } else {
                    image.errorProperty().addListener((observable, previous, failed) -> {
                        if (failed) {
                            imageFailure.set(image.getException());
                            imageFinished.countDown();
                        }
                    });
                    image.progressProperty().addListener((observable, previous, progress) -> {
                        if (progress.doubleValue() >= 1.0) {
                            imageFinished.countDown();
                        }
                    });
                }
                flowReference.set(new OpenFlow(
                        stage, navigation, startRoot, catalogueRoot, passportRoot, detailsRoot, imageView
                ));
            } catch (Exception | Error failure) {
                stage.close();
                throw failure;
            }
        });

        OpenFlow flow = flowReference.get();
        try {
            assertTrue(imageFinished.await(10, TimeUnit.SECONDS), "Passport image did not finish loading");
            assertNull(imageFailure.get(), "Passport image failed to load");
            onFxThread(() -> {
                assertFalse(flow.imageView().getImage().isError());
                assertEquals(1.0, flow.imageView().getImage().getProgress());
                assertTrue(flow.navigation().canGoBack());

                flow.navigation().goBack();
                assertSame(flow.passportRoot(), flow.stage().getScene().getRoot());
                flow.navigation().goBack();
                assertSame(flow.catalogueRoot(), flow.stage().getScene().getRoot());
                flow.navigation().goBack();
                assertSame(flow.startRoot(), flow.stage().getScene().getRoot());
                assertFalse(flow.navigation().canGoBack());
            });
        } finally {
            onFxThread(flow.stage()::close);
        }
    }

    @Test
    void presentsMissingRecordsWithoutBreakingNavigation() throws Exception {
        Path database = temporaryDirectory.resolve("missing-records-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                CatalogueControllerFactory factory = CatalogueControllerFactory.available(
                        context,
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(View.START);
                Parent startRoot = stage.getScene().getRoot();

                navigation.show(
                        View.PASSPORTS,
                        PassportListController.class,
                        controller -> controller.showCountry("entity:missing")
                );
                Parent passportRoot = stage.getScene().getRoot();
                assertEquals(
                        RESOURCES.getString("error.entityMissing"),
                        control(passportRoot, "#messageLabel", Label.class).getText()
                );
                assertTrue(control(passportRoot, "#documentList", ListView.class).getItems().isEmpty());
                navigation.goBack();
                assertSame(startRoot, stage.getScene().getRoot());

                navigation.show(
                        View.PASSPORT_DETAILS,
                        PassportDetailsController.class,
                        controller -> controller.showDocument("document:missing")
                );
                Parent detailsRoot = stage.getScene().getRoot();
                assertEquals(
                        RESOURCES.getString("error.documentMissing"),
                        control(detailsRoot, "#errorLabel", Label.class).getText()
                );
                navigation.goBack();
                assertSame(startRoot, stage.getScene().getRoot());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void presentsMissingImageFilesAsAUserVisibleError() throws Exception {
        Path database = temporaryDirectory.resolve("missing-image-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        String documentId = context.countryService().findAll().stream()
                .flatMap(country -> context.passportService().findByCountryId(country.id()).stream())
                .findFirst()
                .orElseThrow()
                .id();
        Path emptyAssetRoot = temporaryDirectory.resolve("empty-assets");

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                CatalogueControllerFactory factory = CatalogueControllerFactory.available(
                        context,
                        new FileSystemPassportImageLocator(emptyAssetRoot),
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(
                        View.PASSPORT_DETAILS,
                        PassportDetailsController.class,
                        controller -> controller.showDocument(documentId)
                );

                Parent root = stage.getScene().getRoot();
                assertEquals(
                        RESOURCES.getString("error.imageMissing"),
                        control(root, "#errorLabel", Label.class).getText()
                );
                assertNull(control(root, "#passportImageView", ImageView.class).getImage());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void pagesThroughEveryImageWhenADocumentHasMultipleImages() throws Exception {
        Path database = temporaryDirectory.resolve("multiple-images-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext baseContext = CatalogueApplicationContext.openReadOnly(database);
        PassportDocument sourceDocument = baseContext.countryService().findAll().stream()
                .flatMap(country -> baseContext.passportService().findByCountryId(country.id()).stream())
                .findFirst()
                .orElseThrow();
        PassportImage sourceImage = baseContext.passportService().findImages(sourceDocument.id()).getFirst();
        PassportDocument document = new PassportDocument(
                sourceDocument.id(),
                sourceDocument.countryId(),
                sourceDocument.documentType(),
                sourceDocument.title(),
                sourceDocument.variant(),
                sourceDocument.sourceRelativePath(),
                sourceDocument.reviewStatus(),
                2
        );
        PassportImage secondImage = new PassportImage(
                sourceImage.id() + ":second",
                sourceImage.passportDocumentId(),
                sourceImage.relativePath(),
                sourceImage.originalFileName(),
                sourceImage.mimeType(),
                sourceImage.width(),
                sourceImage.height(),
                sourceImage.fileSize(),
                sourceImage.sha256()
        );
        PassportService passportService = new PassportService(new PassportRepository() {
            @Override
            public Optional<PassportDocument> findById(String id) {
                return document.id().equals(id) ? Optional.of(document) : Optional.empty();
            }

            @Override
            public List<PassportDocument> findByCountryId(String countryId) {
                return document.countryId().equals(countryId) ? List.of(document) : List.of();
            }

            @Override
            public List<PassportImage> findImagesByDocumentId(String passportDocumentId) {
                return document.id().equals(passportDocumentId)
                        ? List.of(sourceImage, secondImage)
                        : List.of();
            }
        });
        CatalogueApplicationContext context = new CatalogueApplicationContext(
                baseContext.continentService(),
                baseContext.countryService(),
                passportService
        );

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                CatalogueControllerFactory factory = CatalogueControllerFactory.available(
                        context,
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(
                        View.PASSPORT_DETAILS,
                        PassportDetailsController.class,
                        controller -> controller.showDocument(document.id())
                );

                Parent root = stage.getScene().getRoot();
                Button previous = control(root, "#previousButton", Button.class);
                Button next = control(root, "#nextButton", Button.class);
                Label counter = control(root, "#imageCounterLabel", Label.class);
                ImageView imageView = control(root, "#passportImageView", ImageView.class);
                Image firstImage = imageView.getImage();
                String firstIndicator = counter.getText();
                assertNotNull(firstImage);
                assertTrue(previous.isDisabled());
                assertFalse(next.isDisabled());

                next.fire();
                Image secondImageShown = imageView.getImage();
                assertNotNull(secondImageShown);
                assertNotSame(firstImage, secondImageShown);
                assertNotEquals(firstIndicator, counter.getText());
                assertFalse(previous.isDisabled());
                assertTrue(next.isDisabled());

                previous.fire();
                assertNotNull(imageView.getImage());
                assertNotSame(secondImageShown, imageView.getImage());
                assertEquals(firstIndicator, counter.getText());
                assertTrue(previous.isDisabled());
                assertFalse(next.isDisabled());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void browsesInteractiveMapFallbackListAndExistingPassportFlow() throws Exception {
        Path database = temporaryDirectory.resolve("world-map-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                CatalogueControllerFactory factory = CatalogueControllerFactory.available(
                        context,
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(View.START);
                stage.show();

                Parent startRoot = stage.getScene().getRoot();
                Button mapButton = control(startRoot, "#mapButton", Button.class);
                assertFalse(mapButton.isDisabled());
                mapButton.fire();

                Parent mapRoot = stage.getScene().getRoot();
                mapRoot.applyCss();
                mapRoot.layout();
                StackPane mapHost = control(mapRoot, "#mapHost", StackPane.class);
                WorldMapPane mapPane = (WorldMapPane) mapHost.getChildren().getFirst();
                SVGPath ukraine = control(mapRoot, "#region-UKR", SVGPath.class);
                Bounds initialUkraineBounds = sceneBounds(ukraine);
                ListView<?> entities = control(mapRoot, "#entityList", ListView.class);
                TextField search = control(mapRoot, "#searchField", TextField.class);
                assertEquals(249, mapRoot.lookupAll(".map-region").size());
                assertEquals(139, entities.getItems().size());
                assertEquals(6, control(mapRoot, "#groupButtons", FlowPane.class).getChildren().size());
                assertTrue(mapPane.isFocusTraversable());
                assertEquals(RESOURCES.getString("map.searchPrompt"), search.getAccessibleText());
                assertTrue(ukraine.getAccessibleText().contains(
                        RESOURCES.getString("reviewStatus.AUTO_CLASSIFIED")
                ));

                ToggleButton asia = control(mapRoot, "#mapGroup-ASIA", ToggleButton.class);
                asia.fire();
                assertTrue(asia.isSelected());
                assertTrue(entities.getItems().stream()
                        .map(Country.class::cast)
                        .anyMatch(country -> country.displayName().equals("Корея (неуточнено)")));

                control(mapRoot, "#resetMapButton", Button.class).fire();
                assertEquals(initialUkraineBounds.getWidth(), sceneBounds(ukraine).getWidth(), 0.001);
                search.setText(Normalizer.normalize("уКрАїНа", Normalizer.Form.NFD));
                assertEquals(1, entities.getItems().size());
                assertEquals("Україна", ((Country) entities.getItems().getFirst()).displayName());
                search.clear();

                ukraine.fireEvent(mouseEvent(MouseEvent.MOUSE_ENTERED, MouseButton.NONE, 0));
                assertTrue(ukraine.getPseudoClassStates().contains(PseudoClass.getPseudoClass("hovered")));
                ukraine.fireEvent(mouseEvent(MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY, 1));
                assertEquals(
                        "Україна",
                        ((Country) entities.getSelectionModel().getSelectedItem()).displayName()
                );
                assertTrue(ukraine.getPseudoClassStates().contains(PseudoClass.getPseudoClass("selected")));
                assertEquals(javafx.scene.AccessibleRole.BUTTON, ukraine.getAccessibleRole());

                double widthBeforeZoom = sceneBounds(ukraine).getWidth();
                control(mapRoot, "#zoomInButton", Button.class).fire();
                assertTrue(sceneBounds(ukraine).getWidth() > widthBeforeZoom);

                SVGPath cyprus = control(mapRoot, "#region-CYP", SVGPath.class);
                cyprus.fireEvent(new KeyEvent(
                        KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false
                ));
                assertEquals(
                        "Кіпр",
                        ((Country) entities.getSelectionModel().getSelectedItem()).displayName()
                );
                SVGPath aruba = control(mapRoot, "#region-ABW", SVGPath.class);
                aruba.fireEvent(mouseEvent(MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY, 1));
                assertEquals(
                        "Кіпр",
                        ((Country) entities.getSelectionModel().getSelectedItem()).displayName()
                );
                assertEquals(javafx.scene.AccessibleRole.NODE, aruba.getAccessibleRole());

                asia.fire();
                search.setText("Північний Кіпр");
                assertEquals(1, entities.getItems().size());
                entities.getSelectionModel().selectFirst();
                control(mapRoot, "#openButton", Button.class).fire();
                Parent passportsRoot = stage.getScene().getRoot();
                assertFalse(control(passportsRoot, "#documentList", ListView.class).getItems().isEmpty());
                navigation.goBack();
                assertSame(mapRoot, stage.getScene().getRoot());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void keepsDatelineGroupVisibleInteractiveAndNavigableAcrossTheFullFlow() throws Exception {
        Path database = temporaryDirectory.resolve("wrapped-map-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                CatalogueControllerFactory factory = CatalogueControllerFactory.available(
                        context,
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(View.START);
                stage.show();

                Parent startRoot = stage.getScene().getRoot();
                control(startRoot, "#mapButton", Button.class).fire();
                Parent mapRoot = stage.getScene().getRoot();
                mapRoot.applyCss();
                mapRoot.layout();
                WorldMapPane mapPane = (WorldMapPane) control(
                        mapRoot, "#mapHost", StackPane.class
                ).getChildren().getFirst();
                ListView<?> entities = control(mapRoot, "#entityList", ListView.class);
                ToggleButton oceania = control(
                        mapRoot, "#mapGroup-AUSTRALIA_OCEANIA", ToggleButton.class
                );

                oceania.fire();
                mapRoot.applyCss();
                mapRoot.layout();

                Bounds viewport = mapPane.localToScene(mapPane.getLayoutBounds());
                assertContained(viewport, sceneBounds(control(
                        mapRoot, "#region-AUS", SVGPath.class
                )), "Australia");
                assertContained(viewport, sceneBounds(control(
                        mapRoot, "#wrapped-region-TON", SVGPath.class
                )), "Tonga");
                assertContained(viewport, sceneBounds(control(
                        mapRoot, "#wrapped-region-WSM", SVGPath.class
                )), "Samoa");
                assertVisibleGeometry(mapRoot, "#region-NZL", viewport, "New Zealand main geometry");
                assertVisibleGeometry(
                        mapRoot, "#wrapped-region-NZL", viewport, "New Zealand seam geometry"
                );
                assertVisibleGeometry(mapRoot, "#region-FSM", viewport, "Micronesia");
                assertVisibleGeometry(mapRoot, "#region-TUV", viewport, "Tuvalu");
                assertVisibleGeometry(mapRoot, "#region-PNG", viewport, "Papua New Guinea");

                mapPane.resize(640, 420);
                mapPane.layout();
                Bounds resizedViewport = mapPane.localToScene(mapPane.getLayoutBounds());
                SVGPath wrappedTonga = control(mapRoot, "#wrapped-region-TON", SVGPath.class);
                SVGPath wrappedSamoa = control(mapRoot, "#wrapped-region-WSM", SVGPath.class);
                assertContained(resizedViewport, sceneBounds(wrappedTonga), "Tonga after resize");
                assertContained(resizedViewport, sceneBounds(wrappedSamoa), "Samoa after resize");
                assertVisibleGeometry(
                        mapRoot, "#region-NZL", resizedViewport, "New Zealand after resize"
                );

                double tongaWidthBeforeZoom = sceneBounds(wrappedTonga).getWidth();
                control(mapRoot, "#zoomInButton", Button.class).fire();
                assertTrue(sceneBounds(wrappedTonga).getWidth() > tongaWidthBeforeZoom);
                double tongaXBeforePan = sceneBounds(wrappedTonga).getMinX();
                mapPane.fireEvent(new KeyEvent(
                        KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false
                ));
                assertNotEquals(tongaXBeforePan, sceneBounds(wrappedTonga).getMinX());
                assertTrue(resizedViewport.intersects(sceneBounds(wrappedTonga)));

                wrappedSamoa.fireEvent(mouseEvent(
                        MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY, 1
                ));
                assertEquals(
                        "Самоа",
                        ((Country) entities.getSelectionModel().getSelectedItem()).displayName()
                );
                Country tonga = entities.getItems().stream()
                        .map(Country.class::cast)
                        .filter(country -> country.displayName().equals("Тонга"))
                        .findFirst()
                        .orElseThrow();
                entities.getSelectionModel().select(entities.getItems().indexOf(tonga));
                assertTrue(wrappedTonga.getPseudoClassStates().contains(
                        PseudoClass.getPseudoClass("selected")
                ));

                control(mapRoot, "#openButton", Button.class).fire();
                Parent passportsRoot = stage.getScene().getRoot();
                ListView<?> documents = control(passportsRoot, "#documentList", ListView.class);
                assertFalse(documents.getItems().isEmpty());
                documents.getSelectionModel().selectFirst();
                control(passportsRoot, "#openButton", Button.class).fire();
                Parent detailsRoot = stage.getScene().getRoot();
                assertNotNull(control(detailsRoot, "#passportImageView", ImageView.class));

                navigation.goBack();
                assertSame(passportsRoot, stage.getScene().getRoot());
                navigation.goBack();
                assertSame(mapRoot, stage.getScene().getRoot());
                assertTrue(oceania.isSelected());
                assertNotNull(mapRoot.lookup("#wrapped-region-TON"));

                control(mapRoot, "#resetMapButton", Button.class).fire();
                assertFalse(oceania.isSelected());
                assertNull(mapRoot.lookup("#wrapped-region-TON"));
                assertEquals(249, mapRoot.lookupAll(".map-region").size());
                navigation.goBack();
                assertSame(startRoot, stage.getScene().getRoot());
                assertFalse(navigation.canGoBack());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void keepsCatalogueAvailableWhenMapValidationFails() throws Exception {
        Path database = temporaryDirectory.resolve("map-unavailable-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                CatalogueControllerFactory factory = CatalogueControllerFactory.available(
                        context,
                        new FileSystemPassportImageLocator(temporaryDirectory),
                        resourcesWithoutMapGroupLabel()
                );
                NavigationService navigation = new NavigationService(
                        stage, resourcesWithoutMapGroupLabel(), factory
                );
                navigation.show(View.START);
                stage.show();

                Parent startRoot = stage.getScene().getRoot();
                assertFalse(control(startRoot, "#catalogueButton", Button.class).isDisabled());
                assertTrue(control(startRoot, "#mapButton", Button.class).isDisabled());
                assertEquals(
                        RESOURCES.getString("error.mapUnavailable"),
                        control(startRoot, "#mapStatusLabel", Label.class).getText()
                );
                control(startRoot, "#catalogueButton", Button.class).fire();
                Parent catalogueRoot = stage.getScene().getRoot();
                assertNotNull(control(catalogueRoot, "#continentList", ListView.class));
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void disablesCatalogueEntryWhenReadOnlyCatalogueCannotOpen() throws Exception {
        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                CatalogueControllerFactory factory = CatalogueControllerFactory.unavailable(
                        new SQLException("catalogue fixture unavailable"),
                        new FileSystemPassportImageLocator(temporaryDirectory),
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(View.START);

                Parent root = stage.getScene().getRoot();
                assertTrue(control(root, "#catalogueButton", Button.class).isDisabled());
                assertTrue(control(root, "#mapButton", Button.class).isDisabled());
                assertEquals(
                        RESOURCES.getString("error.catalogueUnavailable"),
                        control(root, "#statusLabel", Label.class).getText()
                );
                assertEquals(
                        RESOURCES.getString("error.mapUnavailable"),
                        control(root, "#mapStatusLabel", Label.class).getText()
                );
            } finally {
                stage.close();
            }
        });
    }

    private static ResourceBundle resourcesWithoutMapGroupLabel() {
        return new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                if ("map.group.europe".equals(key)) {
                    return null;
                }
                return RESOURCES.containsKey(key) ? RESOURCES.getObject(key) : null;
            }

            @Override
            public Enumeration<String> getKeys() {
                return java.util.Collections.enumeration(
                        RESOURCES.keySet().stream()
                                .filter(key -> !"map.group.europe".equals(key))
                                .toList()
                );
            }
        };
    }

    private static Bounds sceneBounds(SVGPath path) {
        return path.localToScene(path.getBoundsInLocal());
    }

    private static void assertContained(Bounds viewport, Bounds content, String description) {
        assertTrue(
                viewport.getMinX() <= content.getMinX()
                        && viewport.getMinY() <= content.getMinY()
                        && viewport.getMaxX() >= content.getMaxX()
                        && viewport.getMaxY() >= content.getMaxY(),
                description + " is outside the focused viewport"
        );
    }

    private static void assertVisibleGeometry(
            Parent root,
            String selector,
            Bounds viewport,
            String description
    ) {
        SVGPath path = control(root, selector, SVGPath.class);
        Matcher matcher = SVG_NUMBER.matcher(path.getContent());
        boolean visible = false;
        while (matcher.find()) {
            double x = Double.parseDouble(matcher.group());
            if (!matcher.find()) {
                break;
            }
            double y = Double.parseDouble(matcher.group());
            Point2D point = path.localToScene(x, y);
            if (viewport.contains(point)) {
                visible = true;
                break;
            }
        }
        assertTrue(visible, description + " has no visible geometry in the focused viewport");
    }

    private static MouseEvent mouseEvent(
            javafx.event.EventType<MouseEvent> type,
            MouseButton button,
            int clickCount
    ) {
        return new MouseEvent(
                type,
                0, 0, 0, 0,
                button,
                clickCount,
                false, false, false, false,
                button == MouseButton.PRIMARY,
                false, false, false, false, true,
                null
        );
    }

    private static <T> T control(Parent root, String selector, Class<T> type) {
        return type.cast(root.lookup(selector));
    }

    private record OpenFlow(
            Stage stage,
            NavigationService navigation,
            Parent startRoot,
            Parent catalogueRoot,
            Parent passportRoot,
            Parent detailsRoot,
            ImageView imageView
    ) {
    }

    private static void onFxThread(ThrowingRunnable action) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                finished.countDown();
            }
        });
        assertTrue(finished.await(10, TimeUnit.SECONDS), "JavaFX operation did not finish");
        Throwable throwable = failure.get();
        if (throwable instanceof Exception exception) {
            throw exception;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
