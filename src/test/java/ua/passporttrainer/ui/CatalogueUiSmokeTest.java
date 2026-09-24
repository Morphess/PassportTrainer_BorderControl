package ua.passporttrainer.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Spinner;
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
import ua.passporttrainer.controller.ApplicationControllerFactory;
import ua.passporttrainer.controller.LearningBrowserController;
import ua.passporttrainer.controller.PassportDetailsController;
import ua.passporttrainer.controller.PassportListController;
import ua.passporttrainer.controller.SessionAbandonmentConfirmation;
import ua.passporttrainer.image.FileSystemPassportImageLocator;
import ua.passporttrainer.image.PassportImageLocator;
import ua.passporttrainer.map.WorldMapPane;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.ExamResult;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.model.QuizCandidate;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSessionLifecycle;
import ua.passporttrainer.presentation.LearningEntityPresentation;
import ua.passporttrainer.presentation.TrainingScopePresentation;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.repository.PassportRepository;
import ua.passporttrainer.repository.RepositoryAccessException;
import ua.passporttrainer.repository.ResultRepository;
import ua.passporttrainer.service.ExamResultService;
import ua.passporttrainer.service.ExaminationConfigurationFactory;
import ua.passporttrainer.service.GeneratedQuestionSetProvider;
import ua.passporttrainer.service.PassportService;
import ua.passporttrainer.service.QuizGenerationService;
import ua.passporttrainer.service.QuizSessionCoordinator;
import ua.passporttrainer.service.QuizSessionLifecycleService;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
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
                ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                        context,
                        sessionCoordinator(context),
                        examResultService(),
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        ignored -> true,
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
                ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                        context,
                        sessionCoordinator(context),
                        examResultService(),
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        ignored -> true,
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
                ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                        context,
                        sessionCoordinator(context),
                        examResultService(),
                        new FileSystemPassportImageLocator(emptyAssetRoot),
                        ignored -> true,
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

            @Override
            public List<QuizCandidate> findQuizEligibleCandidates() {
                return List.of();
            }
        });
        CatalogueApplicationContext context = new CatalogueApplicationContext(
                baseContext.continentService(),
                baseContext.countryService(),
                passportService,
                baseContext.learningService(),
                baseContext.quizGenerationService()
        );

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                        context,
                        sessionCoordinator(context),
                        examResultService(),
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        ignored -> true,
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
                ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                        context,
                        sessionCoordinator(context),
                        examResultService(),
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        ignored -> true,
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
                        RESOURCES.getString("reviewStatus.VERIFIED")
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
                ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                        context,
                        sessionCoordinator(context),
                        examResultService(),
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        ignored -> true,
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
                ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                        context,
                        sessionCoordinator(context),
                        examResultService(),
                        new FileSystemPassportImageLocator(temporaryDirectory),
                        ignored -> true,
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
                ApplicationControllerFactory factory = ApplicationControllerFactory.unavailable(
                        new SQLException("catalogue fixture unavailable"),
                        new FileSystemPassportImageLocator(temporaryDirectory),
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(View.START);

                Parent root = stage.getScene().getRoot();
                assertTrue(control(root, "#catalogueButton", Button.class).isDisabled());
                assertTrue(control(root, "#modeSelectionButton", Button.class).isDisabled());
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

    @Test
    void completesNonScoredLearningFlowAndPreservesNavigation() throws Exception {
        Path database = temporaryDirectory.resolve("learning-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                        context,
                        coordinator,
                        examResultService(),
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        ignored -> true,
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(View.START);
                stage.show();

                Parent startRoot = stage.getScene().getRoot();
                Button modeSelectionButton = control(startRoot, "#modeSelectionButton", Button.class);
                assertFalse(modeSelectionButton.isDisabled());
                modeSelectionButton.fire();

                Parent modeRoot = stage.getScene().getRoot();
                assertFalse(control(modeRoot, "#learningButton", Button.class).isDisabled());
                assertFalse(control(modeRoot, "#trainingButton", Button.class).isDisabled());
                assertFalse(control(modeRoot, "#examinationButton", Button.class).isDisabled());
                control(modeRoot, "#learningButton", Button.class).fire();

                Parent selectionRoot = stage.getScene().getRoot();
                ListView<?> entityList = control(selectionRoot, "#entityList", ListView.class);
                assertEquals(4, entityList.getItems().size());
                LearningEntityPresentation ukraine = entityList.getItems().stream()
                        .map(LearningEntityPresentation.class::cast)
                        .filter(entity -> entity.displayName().equals("Україна"))
                        .findFirst()
                        .orElseThrow();
                entityList.getSelectionModel().select(entityList.getItems().indexOf(ukraine));
                control(selectionRoot, "#openButton", Button.class).fire();

                Parent learningRoot = stage.getScene().getRoot();
                assertEquals("Україна", control(learningRoot, "#headingLabel", Label.class).getText());
                assertEquals(
                        MessageFormat.format(RESOURCES.getString("learning.documentCounter"), 1, 4),
                        control(learningRoot, "#documentCounterLabel", Label.class).getText()
                );
                Label documentType = control(learningRoot, "#documentTypeLabel", Label.class);
                assertFalse(documentType.getText().contains("_"));
                assertNotNull(control(learningRoot, "#passportImageView", ImageView.class).getImage());
                Button previous = control(learningRoot, "#previousButton", Button.class);
                Button next = control(learningRoot, "#nextButton", Button.class);
                assertTrue(previous.isDisabled());
                previous.fire();
                assertFalse(next.isDisabled());
                next.fire();
                assertEquals(
                        MessageFormat.format(RESOURCES.getString("learning.documentCounter"), 2, 4),
                        control(learningRoot, "#documentCounterLabel", Label.class).getText()
                );
                assertFalse(previous.isDisabled());
                previous.fire();
                assertEquals(
                        MessageFormat.format(RESOURCES.getString("learning.documentCounter"), 1, 4),
                        control(learningRoot, "#documentCounterLabel", Label.class).getText()
                );
                while (!next.isDisabled()) {
                    next.fire();
                }
                String finalCounter = control(learningRoot, "#documentCounterLabel", Label.class).getText();
                assertEquals(
                        MessageFormat.format(RESOURCES.getString("learning.documentCounter"), 4, 4),
                        finalCounter
                );
                next.fire();
                assertEquals(finalCounter, control(
                        learningRoot, "#documentCounterLabel", Label.class
                ).getText());
                assertTrue(coordinator.lifecycle().isEmpty());

                control(learningRoot, "#backButton", Button.class).fire();
                assertSame(selectionRoot, stage.getScene().getRoot());
                control(selectionRoot, "#backButton", Button.class).fire();
                assertSame(modeRoot, stage.getScene().getRoot());
                control(modeRoot, "#backButton", Button.class).fire();
                assertSame(startRoot, stage.getScene().getRoot());
                assertTrue(coordinator.lifecycle().isEmpty());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void learningKeepsNavigationAvailableWhenAnImageIsMissing() throws Exception {
        Path database = temporaryDirectory.resolve("learning-missing-image.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        String ukraineId = context.learningService().findEligibleEntities().stream()
                .filter(entity -> entity.displayName().equals("Україна"))
                .map(LearningEntityPresentation::entityId)
                .findFirst()
                .orElseThrow();

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                NavigationService navigation = new NavigationService(
                        stage,
                        RESOURCES,
                        ApplicationControllerFactory.available(
                                context,
                                sessionCoordinator(context),
                                examResultService(),
                                new FileSystemPassportImageLocator(temporaryDirectory),
                                ignored -> true,
                                RESOURCES
                        )
                );
                navigation.show(
                        View.LEARNING_BROWSER,
                        LearningBrowserController.class,
                        controller -> controller.showEntity(ukraineId)
                );

                Parent root = stage.getScene().getRoot();
                assertEquals(
                        RESOURCES.getString("error.imageMissing"),
                        control(root, "#errorLabel", Label.class).getText()
                );
                assertFalse(control(root, "#nextButton", Button.class).isDisabled());
                control(root, "#nextButton", Button.class).fire();
                assertEquals(
                        RESOURCES.getString("error.imageMissing"),
                        control(root, "#errorLabel", Label.class).getText()
                );
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void completesVerticalTrainingWithImmediateFeedbackProgressResultAndMainMenu() throws Exception {
        Path database = temporaryDirectory.resolve("training-vertical-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);
        AtomicReference<TrainingUi> uiReference = new AtomicReference<>();
        AtomicReference<Parent> questionRootReference = new AtomicReference<>();

        onFxThread(() -> {
            TrainingUi ui = openTrainingConfiguration(
                    context,
                    coordinator,
                    new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                    ignored -> true
            );
            uiReference.set(ui);
            Parent modeRoot = ui.modeRoot();
            assertFalse(control(modeRoot, "#trainingButton", Button.class).isDisabled());
            assertFalse(control(modeRoot, "#examinationButton", Button.class).isDisabled());

            Parent configurationRoot = ui.configurationRoot();
            assertEquals(5, control(
                    configurationRoot, "#questionCountSpinner", Spinner.class
            ).getValue());
            assertEquals("4", control(configurationRoot, "#optionCountLabel", Label.class).getText());
            assertTrue(control(
                    configurationRoot, "#scopeComboBox", ComboBox.class
            ).getValue() instanceof TrainingScopePresentation scope && scope.entityId().isEmpty());
            configureQuestionType(configurationRoot, QuizQuestionType.DOCUMENT_TYPE, 2);
            control(configurationRoot, "#startTrainingButton", Button.class).fire();

            Parent questionRoot = ui.stage().getScene().getRoot();
            questionRootReference.set(questionRoot);
            assertTrue(control(questionRoot, "#submitButton", Button.class).isDisabled());
            assertTrue(control(questionRoot, "#nextButton", Button.class).isDisabled());
            assertEquals(
                    MessageFormat.format(RESOURCES.getString("training.progress"), 1, 2),
                    control(questionRoot, "#progressLabel", Label.class).getText()
            );
            String correctId = coordinator.currentQuestion().correctAnswerId();
            String wrongId = questionOptions(questionRoot).stream()
                    .map(option -> (String) option.getUserData())
                    .filter(id -> !id.equals(correctId))
                    .findFirst()
                    .orElseThrow();
            selectQuestionOption(questionRoot, wrongId);
        });

        awaitFxCondition(() -> !control(
                questionRootReference.get(), "#submitButton", Button.class
        ).isDisabled());
        onFxThread(() -> {
            TrainingUi ui = uiReference.get();
            Parent questionRoot = questionRootReference.get();
            long sessionId = coordinator.session().orElseThrow().id();
            long seed = coordinator.randomSeed().orElseThrow();
            control(questionRoot, "#submitButton", Button.class).fire();

            assertEquals(
                    RESOURCES.getString("training.incorrect"),
                    control(questionRoot, "#feedbackLabel", Label.class).getText()
            );
            assertTrue(questionOptions(questionRoot).stream().allMatch(ToggleButton::isDisabled));
            assertTrue(questionOptions(questionRoot).stream()
                    .anyMatch(option -> option.getStyleClass().contains("correct-answer")));
            assertTrue(questionOptions(questionRoot).stream()
                    .anyMatch(option -> option.getStyleClass().contains("incorrect-answer")));
            assertFalse(control(questionRoot, "#nextButton", Button.class).isDisabled());
            control(questionRoot, "#nextButton", Button.class).fire();

            assertSame(questionRoot, ui.stage().getScene().getRoot());
            assertEquals(sessionId, coordinator.session().orElseThrow().id());
            assertEquals(seed, coordinator.randomSeed().orElseThrow());
            assertEquals(1, coordinator.session().orElseThrow().answers().size());
            assertEquals(
                    MessageFormat.format(RESOURCES.getString("training.progress"), 2, 2),
                    control(questionRoot, "#progressLabel", Label.class).getText()
            );
            selectQuestionOption(questionRoot, coordinator.currentQuestion().correctAnswerId());
        });

        awaitFxCondition(() -> !control(
                questionRootReference.get(), "#submitButton", Button.class
        ).isDisabled());
        onFxThread(() -> {
            TrainingUi ui = uiReference.get();
            Parent questionRoot = questionRootReference.get();
            control(questionRoot, "#submitButton", Button.class).fire();
            assertEquals(
                    RESOURCES.getString("training.correct"),
                    control(questionRoot, "#feedbackLabel", Label.class).getText()
            );
            control(questionRoot, "#nextButton", Button.class).fire();

            Parent resultRoot = ui.stage().getScene().getRoot();
            assertEquals(1, Integer.parseInt(control(
                    resultRoot, "#correctCountLabel", Label.class
            ).getText()));
            assertEquals(1, Integer.parseInt(control(
                    resultRoot, "#incorrectCountLabel", Label.class
            ).getText()));
            assertEquals("2", control(resultRoot, "#totalCountLabel", Label.class).getText());
            assertEquals("50%", control(resultRoot, "#percentageLabel", Label.class).getText());
            assertEquals(QuizSessionLifecycle.COMPLETED, coordinator.lifecycle().orElseThrow());
            control(resultRoot, "#mainMenuButton", Button.class).fire();

            Parent resetRoot = ui.stage().getScene().getRoot();
            assertNotNull(resetRoot.lookup("#modeSelectionButton"));
            assertFalse(ui.navigation().canGoBack());
            assertTrue(coordinator.session().isEmpty());
            ui.stage().close();
        });
    }
    @Test
    void completesVerticalExaminationWithoutDisclosureAndResetsToMainMenu() throws Exception {
        Path database = temporaryDirectory.resolve("examination-vertical-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);
        TestResultRepository resultRepository = new TestResultRepository(true);
        AtomicReference<Stage> stageReference = new AtomicReference<>();
        AtomicReference<NavigationService> navigationReference = new AtomicReference<>();
        AtomicReference<Parent> questionRootReference = new AtomicReference<>();

        onFxThread(() -> {
            Stage stage = new Stage();
            stageReference.set(stage);
            ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                    context,
                    coordinator,
                    new ExamResultService(resultRepository),
                    new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                    ignored -> true,
                    RESOURCES
            );
            NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
            navigationReference.set(navigation);
            navigation.show(View.START);
            stage.show();
            Parent startRoot = stage.getScene().getRoot();
            control(startRoot, "#modeSelectionButton", Button.class).fire();
            Parent modeRoot = stage.getScene().getRoot();
            assertFalse(control(modeRoot, "#examinationButton", Button.class).isDisabled());
            control(modeRoot, "#examinationButton", Button.class).fire();

            Parent confirmationRoot = stage.getScene().getRoot();
            assertEquals("10", control(
                    confirmationRoot, "#questionCountLabel", Label.class
            ).getText());
            assertEquals("4", control(
                    confirmationRoot, "#optionCountLabel", Label.class
            ).getText());
            assertFalse(control(
                    confirmationRoot, "#feedbackPolicyLabel", Label.class
            ).getText().isBlank());
            control(confirmationRoot, "#startExaminationButton", Button.class).fire();

            Parent questionRoot = stage.getScene().getRoot();
            questionRootReference.set(questionRoot);
            assertEquals(QuizMode.EXAMINATION, coordinator.session().orElseThrow().mode());
            assertEquals(10, coordinator.session().orElseThrow().questions().size());
            assertTrue(coordinator.visibleScore().isEmpty());
            selectQuestionOption(questionRoot, coordinator.currentQuestion().correctAnswerId());
        });

        for (int index = 0; index < 10; index++) {
            awaitFxCondition(() -> !control(
                    questionRootReference.get(), "#submitButton", Button.class
            ).isDisabled());
            int questionIndex = index;
            onFxThread(() -> {
                Parent questionRoot = questionRootReference.get();
                assertSame(questionRoot, stageReference.get().getScene().getRoot());
                assertTrue(coordinator.visibleScore().isEmpty());
                control(questionRoot, "#submitButton", Button.class).fire();
                assertTrue(coordinator.visibleScore().isEmpty());
                Label feedback = control(questionRoot, "#feedbackLabel", Label.class);
                assertTrue(feedback.getText().isEmpty());
                assertFalse(feedback.isVisible());
                assertTrue(questionOptions(questionRoot).stream().allMatch(ToggleButton::isDisabled));
                assertTrue(questionOptions(questionRoot).stream().noneMatch(option ->
                        option.getStyleClass().contains("correct-answer")
                                || option.getStyleClass().contains("incorrect-answer")
                ));
                assertEquals(0, resultRepository.saveAttempts);
                control(questionRoot, "#nextButton", Button.class).fire();
                if (questionIndex < 9) {
                    assertSame(questionRoot, stageReference.get().getScene().getRoot());
                    assertEquals(
                            questionIndex + 1,
                            coordinator.currentQuestionIndex().orElseThrow()
                    );
                    selectQuestionOption(
                            questionRoot,
                            coordinator.currentQuestion().correctAnswerId()
                    );
                }
            });
        }

        onFxThread(() -> {
            Stage stage = stageReference.get();
            Parent resultRoot = stage.getScene().getRoot();
            assertEquals("10", control(resultRoot, "#correctCountLabel", Label.class).getText());
            assertEquals("0", control(resultRoot, "#incorrectCountLabel", Label.class).getText());
            assertEquals("10", control(resultRoot, "#totalCountLabel", Label.class).getText());
            assertEquals("100%", control(resultRoot, "#percentageLabel", Label.class).getText());
            assertEquals("0 с", control(resultRoot, "#durationLabel", Label.class).getText());
            assertEquals(QuizSessionLifecycle.COMPLETED, coordinator.lifecycle().orElseThrow());
            Button mainMenuButton = control(resultRoot, "#mainMenuButton", Button.class);
            Button retrySaveButton = control(resultRoot, "#retrySaveButton", Button.class);
            assertTrue(mainMenuButton.isDisabled());
            assertTrue(retrySaveButton.isVisible());
            assertTrue(retrySaveButton.isManaged());
            assertEquals(
                    RESOURCES.getString("examination.result.saveFailure"),
                    control(resultRoot, "#persistenceStatusLabel", Label.class).getText()
            );
            assertEquals(10, coordinator.session().orElseThrow().answers().size());
            retrySaveButton.fire();
            assertFalse(mainMenuButton.isDisabled());
            assertFalse(retrySaveButton.isVisible());
            assertEquals(
                    RESOURCES.getString("examination.result.saveSuccess"),
                    control(resultRoot, "#persistenceStatusLabel", Label.class).getText()
            );
            assertEquals(2, resultRepository.saveAttempts);
            assertEquals(coordinator.session().orElseThrow().id(), resultRepository.result.id());
            control(resultRoot, "#mainMenuButton", Button.class).fire();

            Parent resetRoot = stage.getScene().getRoot();
            assertNotNull(resetRoot.lookup("#modeSelectionButton"));
            assertFalse(navigationReference.get().canGoBack());
            assertTrue(coordinator.session().isEmpty());
            stage.close();
        });
    }
    @Test
    void activeExaminationBackCancelPreservesAndConfirmAbandons() throws Exception {
        Path database = temporaryDirectory.resolve("examination-abandon-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);
        Deque<Boolean> confirmationAnswers = new ArrayDeque<>(List.of(false, true));
        AtomicReference<Stage> stageReference = new AtomicReference<>();
        AtomicReference<Parent> confirmationRootReference = new AtomicReference<>();
        AtomicReference<Parent> questionRootReference = new AtomicReference<>();

        onFxThread(() -> {
            Stage stage = new Stage();
            stageReference.set(stage);
            NavigationService navigation = new NavigationService(
                    stage,
                    RESOURCES,
                    ApplicationControllerFactory.available(
                            context,
                            coordinator,
                            examResultService(),
                            new FileSystemPassportImageLocator(
                                    Path.of("Зразки паспортних документів")
                            ),
                            ignored -> confirmationAnswers.removeFirst(),
                            RESOURCES
                    )
            );
            navigation.show(View.MODE_SELECTION);
            stage.show();
            Parent modeRoot = stage.getScene().getRoot();
            control(modeRoot, "#examinationButton", Button.class).fire();
            Parent confirmationRoot = stage.getScene().getRoot();
            confirmationRootReference.set(confirmationRoot);
            control(confirmationRoot, "#startExaminationButton", Button.class).fire();
            Parent questionRoot = stage.getScene().getRoot();
            questionRootReference.set(questionRoot);
            selectQuestionOption(questionRoot, coordinator.currentQuestion().correctAnswerId());
        });

        awaitFxCondition(() -> !control(
                questionRootReference.get(), "#submitButton", Button.class
        ).isDisabled());
        onFxThread(() -> {
            Parent questionRoot = questionRootReference.get();
            control(questionRoot, "#submitButton", Button.class).fire();
            control(questionRoot, "#nextButton", Button.class).fire();
            long sessionId = coordinator.session().orElseThrow().id();
            long seed = coordinator.randomSeed().orElseThrow();
            assertEquals(1, coordinator.currentQuestionIndex().orElseThrow());
            assertEquals(1, coordinator.session().orElseThrow().answers().size());

            control(questionRoot, "#backButton", Button.class).fire();
            assertSame(questionRoot, stageReference.get().getScene().getRoot());
            assertEquals(sessionId, coordinator.session().orElseThrow().id());
            assertEquals(seed, coordinator.randomSeed().orElseThrow());
            assertEquals(1, coordinator.currentQuestionIndex().orElseThrow());
            assertEquals(1, coordinator.session().orElseThrow().answers().size());

            control(questionRoot, "#backButton", Button.class).fire();
            assertSame(
                    confirmationRootReference.get(),
                    stageReference.get().getScene().getRoot()
            );
            assertEquals(QuizSessionLifecycle.CREATED, coordinator.lifecycle().orElseThrow());
            assertTrue(coordinator.session().isEmpty());
            stageReference.get().close();
        });
    }

    @Test
    void examinationTypedGenerationFailureStaysOnConfirmation() throws Exception {
        Path database = temporaryDirectory.resolve("examination-failure-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = new QuizSessionCoordinator(
                new GeneratedQuestionSetProvider(
                        new QuizGenerationService(emptyPassportRepository(), Clock.systemUTC())
                ),
                new QuizSessionLifecycleService(Clock.systemUTC()),
                () -> 1L,
                () -> 2L
        );

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                NavigationService navigation = new NavigationService(
                        stage,
                        RESOURCES,
                        ApplicationControllerFactory.available(
                                context,
                                coordinator,
                                examResultService(),
                                new FileSystemPassportImageLocator(
                                        Path.of("Зразки паспортних документів")
                                ),
                                ignored -> true,
                                RESOURCES
                        )
                );
                navigation.show(View.MODE_SELECTION);
                stage.show();
                control(
                        stage.getScene().getRoot(), "#examinationButton", Button.class
                ).fire();
                Parent confirmationRoot = stage.getScene().getRoot();
                control(
                        confirmationRoot, "#startExaminationButton", Button.class
                ).fire();

                assertSame(confirmationRoot, stage.getScene().getRoot());
                assertEquals(
                        MessageFormat.format(
                                RESOURCES.getString(
                                        "examination.failure.insufficientEligibleData"
                                ),
                                10,
                                0
                        ),
                        control(confirmationRoot, "#errorLabel", Label.class).getText()
                );
                assertEquals(
                        QuizSessionLifecycle.CREATED,
                        coordinator.lifecycle().orElseThrow()
                );
                assertTrue(coordinator.session().isEmpty());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void newExaminationClearsCompletedStateAndReturnsToConfirmation() throws Exception {
        Path database = temporaryDirectory.resolve("examination-new-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                NavigationService navigation = new NavigationService(
                        stage,
                        RESOURCES,
                        ApplicationControllerFactory.available(
                                context,
                                coordinator,
                                examResultService(),
                                new FileSystemPassportImageLocator(
                                        Path.of("Зразки паспортних документів")
                                ),
                                ignored -> true,
                                RESOURCES
                        )
                );
                navigation.show(View.MODE_SELECTION);
                stage.show();
                control(
                        stage.getScene().getRoot(), "#examinationButton", Button.class
                ).fire();
                Parent confirmationRoot = stage.getScene().getRoot();

                coordinator.configure(new ExaminationConfigurationFactory().create());
                coordinator.start();
                for (int index = 0; index < 10; index++) {
                    coordinator.submitAnswer(coordinator.currentQuestion().correctAnswerId());
                    if (index < 9) {
                        coordinator.advance();
                    }
                }
                coordinator.complete();
                navigation.show(View.EXAMINATION_RESULT);
                Parent resultRoot = stage.getScene().getRoot();
                control(resultRoot, "#newExaminationButton", Button.class).fire();

                assertSame(confirmationRoot, stage.getScene().getRoot());
                assertEquals(
                        QuizSessionLifecycle.CREATED,
                        coordinator.lifecycle().orElseThrow()
                );
                assertTrue(coordinator.session().isEmpty());
            } finally {
                stage.close();
            }
        });
    }



    @Test
    void repeatUsesFreshIdentityAndNewTrainingReturnsToConfiguration() throws Exception {
        Path database = temporaryDirectory.resolve("training-repeat-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);
        AtomicReference<TrainingUi> uiReference = new AtomicReference<>();
        AtomicReference<Parent> questionRootReference = new AtomicReference<>();

        onFxThread(() -> {
            TrainingUi ui = openTrainingConfiguration(
                    context,
                    coordinator,
                    new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                    ignored -> true
            );
            uiReference.set(ui);
            configureQuestionType(ui.configurationRoot(), QuizQuestionType.DOCUMENT_TYPE, 1);
            control(ui.configurationRoot(), "#startTrainingButton", Button.class).fire();
            Parent questionRoot = ui.stage().getScene().getRoot();
            questionRootReference.set(questionRoot);
            selectQuestionOption(questionRoot, coordinator.currentQuestion().correctAnswerId());
        });

        awaitFxCondition(() -> !control(
                questionRootReference.get(), "#submitButton", Button.class
        ).isDisabled());
        long[] firstIdentity = new long[2];
        onFxThread(() -> {
            TrainingUi ui = uiReference.get();
            Parent questionRoot = questionRootReference.get();
            firstIdentity[0] = coordinator.session().orElseThrow().id();
            firstIdentity[1] = coordinator.randomSeed().orElseThrow();
            control(questionRoot, "#submitButton", Button.class).fire();
            control(questionRoot, "#nextButton", Button.class).fire();
            Parent resultRoot = ui.stage().getScene().getRoot();
            control(resultRoot, "#repeatButton", Button.class).fire();

            Parent repeatedQuestionRoot = ui.stage().getScene().getRoot();
            questionRootReference.set(repeatedQuestionRoot);
            assertNotEquals(firstIdentity[0], coordinator.session().orElseThrow().id());
            assertNotEquals(firstIdentity[1], coordinator.randomSeed().orElseThrow());
            assertTrue(coordinator.session().orElseThrow().answers().isEmpty());
            selectQuestionOption(repeatedQuestionRoot, coordinator.currentQuestion().correctAnswerId());
        });

        awaitFxCondition(() -> !control(
                questionRootReference.get(), "#submitButton", Button.class
        ).isDisabled());
        onFxThread(() -> {
            TrainingUi ui = uiReference.get();
            Parent repeatedQuestionRoot = questionRootReference.get();
            control(repeatedQuestionRoot, "#submitButton", Button.class).fire();
            control(repeatedQuestionRoot, "#nextButton", Button.class).fire();
            Parent resultRoot = ui.stage().getScene().getRoot();
            control(resultRoot, "#newTrainingButton", Button.class).fire();

            assertSame(ui.configurationRoot(), ui.stage().getScene().getRoot());
            assertEquals(QuizSessionLifecycle.CREATED, coordinator.lifecycle().orElseThrow());
            assertTrue(coordinator.session().isEmpty());
            ui.stage().close();
        });
    }

    @Test
    void activeTrainingBackCancelPreservesStateAndConfirmAbandons() throws Exception {
        Path database = temporaryDirectory.resolve("training-abandon-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);
        Deque<Boolean> confirmationAnswers = new ArrayDeque<>(List.of(false, true));
        AtomicReference<TrainingUi> uiReference = new AtomicReference<>();
        AtomicReference<Parent> questionRootReference = new AtomicReference<>();

        onFxThread(() -> {
            TrainingUi ui = openTrainingConfiguration(
                    context,
                    coordinator,
                    new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                    ignored -> confirmationAnswers.removeFirst()
            );
            uiReference.set(ui);
            configureQuestionType(ui.configurationRoot(), QuizQuestionType.DOCUMENT_TYPE, 2);
            control(ui.configurationRoot(), "#startTrainingButton", Button.class).fire();
            Parent questionRoot = ui.stage().getScene().getRoot();
            questionRootReference.set(questionRoot);
            selectQuestionOption(questionRoot, coordinator.currentQuestion().correctAnswerId());
        });

        awaitFxCondition(() -> !control(
                questionRootReference.get(), "#submitButton", Button.class
        ).isDisabled());
        onFxThread(() -> {
            TrainingUi ui = uiReference.get();
            Parent questionRoot = questionRootReference.get();
            control(questionRoot, "#submitButton", Button.class).fire();
            control(questionRoot, "#nextButton", Button.class).fire();
            assertEquals(1, coordinator.currentQuestionIndex().orElseThrow());
            assertEquals(1, coordinator.session().orElseThrow().answers().size());

            control(questionRoot, "#backButton", Button.class).fire();
            assertSame(questionRoot, ui.stage().getScene().getRoot());
            assertEquals(QuizSessionLifecycle.ACTIVE, coordinator.lifecycle().orElseThrow());
            assertEquals(1, coordinator.currentQuestionIndex().orElseThrow());
            assertEquals(1, coordinator.session().orElseThrow().answers().size());

            control(questionRoot, "#backButton", Button.class).fire();
            assertSame(ui.configurationRoot(), ui.stage().getScene().getRoot());
            assertEquals(QuizSessionLifecycle.CREATED, coordinator.lifecycle().orElseThrow());
            assertTrue(coordinator.session().isEmpty());
            assertTrue(confirmationAnswers.isEmpty());
            ui.stage().close();
        });
    }

    @Test
    void imageCardQuestionKeepsPreSubmitLabelsNeutralAndRevealsFeedback() throws Exception {
        Path database = temporaryDirectory.resolve("training-image-options-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);
        AtomicReference<TrainingUi> uiReference = new AtomicReference<>();
        AtomicReference<Parent> questionRootReference = new AtomicReference<>();

        onFxThread(() -> {
            TrainingUi ui = openTrainingConfiguration(
                    context,
                    coordinator,
                    new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                    ignored -> true
            );
            uiReference.set(ui);
            configureQuestionType(ui.configurationRoot(), QuizQuestionType.COUNTRY_TO_PASSPORT, 1);
            control(ui.configurationRoot(), "#startTrainingButton", Button.class).fire();
            Parent questionRoot = ui.stage().getScene().getRoot();
            questionRootReference.set(questionRoot);

            List<ToggleButton> options = questionOptions(questionRoot);
            assertEquals(4, options.size());
            assertEquals(
                    java.util.Set.of("Варіант 1", "Варіант 2", "Варіант 3", "Варіант 4"),
                    options.stream()
                            .map(ToggleButton::getAccessibleText)
                            .collect(java.util.stream.Collectors.toSet())
            );
            String subjectText = control(
                    questionRoot, "#subjectTextLabel", Label.class
            ).getText();
            assertTrue(options.stream()
                    .map(ToggleButton::getAccessibleText)
                    .noneMatch(label -> label.contains(subjectText)));
            selectQuestionOption(questionRoot, coordinator.currentQuestion().correctAnswerId());
        });

        awaitFxCondition(() -> !control(
                questionRootReference.get(), "#submitButton", Button.class
        ).isDisabled());
        onFxThread(() -> {
            TrainingUi ui = uiReference.get();
            Parent questionRoot = questionRootReference.get();
            control(questionRoot, "#submitButton", Button.class).fire();
            assertEquals(
                    RESOURCES.getString("training.correct"),
                    control(questionRoot, "#feedbackLabel", Label.class).getText()
            );
            assertTrue(questionOptions(questionRoot).stream()
                    .filter(option -> option.getStyleClass().contains("correct-answer"))
                    .allMatch(ToggleButton::isDisabled));
            control(questionRoot, "#nextButton", Button.class).fire();
            assertNotNull(ui.stage().getScene().getRoot().lookup("#mainMenuButton"));
            ui.stage().close();
        });
    }

    @Test
    void learningPracticeHandoffPreservesCanonicalScopeAndTypedFailure() throws Exception {
        Path database = temporaryDirectory.resolve("learning-training-handoff-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                        context,
                        coordinator,
                        examResultService(),
                        new FileSystemPassportImageLocator(Path.of("Зразки паспортних документів")),
                        ignored -> true,
                        RESOURCES
                );
                NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
                navigation.show(View.START);
                stage.show();
                control(stage.getScene().getRoot(), "#modeSelectionButton", Button.class).fire();
                Parent modeRoot = stage.getScene().getRoot();
                control(modeRoot, "#learningButton", Button.class).fire();
                Parent selectionRoot = stage.getScene().getRoot();
                ListView<?> entities = control(selectionRoot, "#entityList", ListView.class);
                LearningEntityPresentation ukraine = entities.getItems().stream()
                        .map(LearningEntityPresentation.class::cast)
                        .filter(entity -> entity.displayName().equals("Україна"))
                        .findFirst()
                        .orElseThrow();
                entities.getSelectionModel().select(entities.getItems().indexOf(ukraine));
                control(selectionRoot, "#openButton", Button.class).fire();
                Parent learningRoot = stage.getScene().getRoot();
                control(learningRoot, "#practiceButton", Button.class).fire();

                Parent configurationRoot = stage.getScene().getRoot();
                ComboBox<TrainingScopePresentation> scopes = control(
                        configurationRoot, "#scopeComboBox", ComboBox.class
                );
                TrainingScopePresentation preselected = scopes.getValue();
                assertEquals(Optional.of(ukraine.entityId()), preselected.entityId());
                assertEquals("Україна", preselected.displayName());
                scopes.getSelectionModel().selectFirst();
                assertTrue(scopes.getValue().entityId().isEmpty());
                scopes.getSelectionModel().select(preselected);
                configureQuestionType(
                        configurationRoot,
                        QuizQuestionType.PASSPORT_TO_COUNTRY,
                        1
                );
                control(configurationRoot, "#startTrainingButton", Button.class).fire();

                assertSame(configurationRoot, stage.getScene().getRoot());
                assertEquals(
                        java.util.Set.of(ukraine.entityId()),
                        coordinator.configuration().orElseThrow().entityIds()
                );
                assertEquals(QuizSessionLifecycle.CREATED, coordinator.lifecycle().orElseThrow());
                assertTrue(coordinator.session().isEmpty());
                String failure = control(configurationRoot, "#errorLabel", Label.class).getText();
                assertTrue(failure.contains(RESOURCES.getString(
                        "questionType.PASSPORT_TO_COUNTRY"
                )));
                assertSame(learningRoot, navigationRootAfterBack(navigation, stage));
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void missingTrainingImageDisablesSubmissionWithoutAnsweringOrSkipping() throws Exception {
        Path database = temporaryDirectory.resolve("training-missing-image-ui.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);
        QuizSessionCoordinator coordinator = sessionCoordinator(context);

        onFxThread(() -> {
            TrainingUi ui = openTrainingConfiguration(
                    context,
                    coordinator,
                    new FileSystemPassportImageLocator(temporaryDirectory.resolve("missing-assets")),
                    ignored -> true
            );
            try {
                configureQuestionType(ui.configurationRoot(), QuizQuestionType.DOCUMENT_TYPE, 1);
                control(ui.configurationRoot(), "#startTrainingButton", Button.class).fire();
                Parent questionRoot = ui.stage().getScene().getRoot();
                selectQuestionOption(questionRoot, coordinator.currentQuestion().options().getFirst().id());

                assertEquals(
                        RESOURCES.getString("training.imageUnavailable"),
                        control(questionRoot, "#errorLabel", Label.class).getText()
                );
                assertTrue(control(questionRoot, "#submitButton", Button.class).isDisabled());
                assertTrue(control(questionRoot, "#nextButton", Button.class).isDisabled());
                assertTrue(coordinator.session().orElseThrow().answers().isEmpty());
                assertEquals(0, coordinator.currentQuestionIndex().orElseThrow());
                control(questionRoot, "#backButton", Button.class).fire();
                assertSame(ui.configurationRoot(), ui.stage().getScene().getRoot());
                assertTrue(coordinator.session().isEmpty());
            } finally {
                ui.stage().close();
            }
        });
    }

    @Test
    void showPreservesExistingBackStackBehavior() throws Exception {
        Path database = temporaryDirectory.resolve("navigation-show.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                NavigationService navigation = new NavigationService(
                        stage,
                        RESOURCES,
                        ApplicationControllerFactory.available(
                                context,
                                sessionCoordinator(context),
                                examResultService(),
                                new FileSystemPassportImageLocator(temporaryDirectory),
                                ignored -> true,
                                RESOURCES
                        )
                );
                navigation.show(View.START);
                Parent startRoot = stage.getScene().getRoot();
                navigation.show(View.CATALOGUE);

                assertTrue(navigation.canGoBack());
                navigation.goBack();
                assertSame(startRoot, stage.getScene().getRoot());
                assertFalse(navigation.canGoBack());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void replaceDoesNotAddTheReplacedRootToHistory() throws Exception {
        Path database = temporaryDirectory.resolve("navigation-replace.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                NavigationService navigation = new NavigationService(
                        stage,
                        RESOURCES,
                        ApplicationControllerFactory.available(
                                context,
                                sessionCoordinator(context),
                                examResultService(),
                                new FileSystemPassportImageLocator(temporaryDirectory),
                                ignored -> true,
                                RESOURCES
                        )
                );
                navigation.show(View.START);
                Parent startRoot = stage.getScene().getRoot();
                navigation.show(View.CATALOGUE);
                Parent catalogueRoot = stage.getScene().getRoot();

                navigation.replace(View.WORLD_MAP);

                assertNotSame(catalogueRoot, stage.getScene().getRoot());
                assertTrue(navigation.canGoBack());
                navigation.goBack();
                assertSame(startRoot, stage.getScene().getRoot());
                assertFalse(navigation.canGoBack());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void resetToClearsNavigationHistory() throws Exception {
        Path database = temporaryDirectory.resolve("navigation-reset.sqlite");
        CatalogueTestSupport.importCatalogue(database);
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(database);

        onFxThread(() -> {
            Stage stage = new Stage();
            try {
                NavigationService navigation = new NavigationService(
                        stage,
                        RESOURCES,
                        ApplicationControllerFactory.available(
                                context,
                                sessionCoordinator(context),
                                examResultService(),
                                new FileSystemPassportImageLocator(temporaryDirectory),
                                ignored -> true,
                                RESOURCES
                        )
                );
                navigation.show(View.START);
                navigation.show(View.CATALOGUE);
                navigation.resetTo(View.START);
                Parent resetRoot = stage.getScene().getRoot();

                assertFalse(navigation.canGoBack());
                navigation.goBack();
                assertSame(resetRoot, stage.getScene().getRoot());
            } finally {
                stage.close();
            }
        });
    }

    private static TrainingUi openTrainingConfiguration(
            CatalogueApplicationContext context,
            QuizSessionCoordinator coordinator,
            PassportImageLocator imageLocator,
            SessionAbandonmentConfirmation abandonmentConfirmation
    ) {
        Stage stage = new Stage();
        ApplicationControllerFactory factory = ApplicationControllerFactory.available(
                context,
                coordinator,
                examResultService(),
                imageLocator,
                abandonmentConfirmation,
                RESOURCES
        );
        NavigationService navigation = new NavigationService(stage, RESOURCES, factory);
        navigation.show(View.START);
        stage.show();
        Parent startRoot = stage.getScene().getRoot();
        control(startRoot, "#modeSelectionButton", Button.class).fire();
        Parent modeRoot = stage.getScene().getRoot();
        control(modeRoot, "#trainingButton", Button.class).fire();
        Parent configurationRoot = stage.getScene().getRoot();
        return new TrainingUi(stage, navigation, modeRoot, configurationRoot);
    }

    private static void configureQuestionType(
            Parent configurationRoot,
            QuizQuestionType selectedType,
            int questionCount
    ) {
        control(
                configurationRoot, "#passportToCountryCheckBox", CheckBox.class
        ).setSelected(selectedType == QuizQuestionType.PASSPORT_TO_COUNTRY);
        control(
                configurationRoot, "#countryToPassportCheckBox", CheckBox.class
        ).setSelected(selectedType == QuizQuestionType.COUNTRY_TO_PASSPORT);
        control(
                configurationRoot, "#documentTypeCheckBox", CheckBox.class
        ).setSelected(selectedType == QuizQuestionType.DOCUMENT_TYPE);
        control(
                configurationRoot, "#findOrdinaryPassportCheckBox", CheckBox.class
        ).setSelected(selectedType == QuizQuestionType.FIND_ORDINARY_PASSPORT);
        @SuppressWarnings("unchecked")
        Spinner<Integer> spinner = control(
                configurationRoot, "#questionCountSpinner", Spinner.class
        );
        spinner.getValueFactory().setValue(questionCount);
    }

    private static List<ToggleButton> questionOptions(Parent questionRoot) {
        return questionRoot.lookupAll(".question-option").stream()
                .map(ToggleButton.class::cast)
                .toList();
    }

    private static void selectQuestionOption(Parent questionRoot, String optionId) {
        ToggleButton option = questionOptions(questionRoot).stream()
                .filter(candidate -> optionId.equals(candidate.getUserData()))
                .findFirst()
                .orElseThrow();
        option.setSelected(true);
    }

    private static Parent navigationRootAfterBack(NavigationService navigation, Stage stage) {
        navigation.goBack();
        return stage.getScene().getRoot();
    }

    private static void awaitFxCondition(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            AtomicBoolean satisfied = new AtomicBoolean();
            onFxThread(() -> satisfied.set(condition.getAsBoolean()));
            if (satisfied.get()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("JavaFX condition was not satisfied within 10 seconds");
    }

    private static QuizSessionCoordinator sessionCoordinator(CatalogueApplicationContext context) {
        AtomicLong sessionIds = new AtomicLong(1L);
        AtomicLong seeds = new AtomicLong(100L);
        return new QuizSessionCoordinator(
                new GeneratedQuestionSetProvider(context.quizGenerationService()),
                new QuizSessionLifecycleService(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
                sessionIds::getAndIncrement,
                seeds::getAndIncrement
        );
    }

    private static ExamResultService examResultService() {
        return new ExamResultService(new TestResultRepository(false));
    }

    private static final class TestResultRepository implements ResultRepository {

        private boolean failNextSave;
        private ExamResult result;
        private int saveAttempts;

        private TestResultRepository(boolean failNextSave) {
            this.failNextSave = failNextSave;
        }

        @Override
        public ExamResult save(ExamResult candidate) {
            saveAttempts++;
            if (failNextSave) {
                failNextSave = false;
                throw new RepositoryAccessException("Simulated result save failure");
            }
            if (result == null) {
                result = candidate;
            }
            return result;
        }

        @Override
        public Optional<ExamResult> findById(long id) {
            return result != null && result.id() == id
                    ? Optional.of(result)
                    : Optional.empty();
        }

        @Override
        public List<ExamResult> findByUserId(long userId) {
            return result != null && result.userId() != null && result.userId() == userId
                    ? List.of(result)
                    : List.of();
        }
    }

    private static PassportRepository emptyPassportRepository() {
        return new PassportRepository() {
            @Override
            public Optional<PassportDocument> findById(String id) {
                return Optional.empty();
            }

            @Override
            public List<PassportDocument> findByCountryId(String countryId) {
                return List.of();
            }

            @Override
            public List<PassportImage> findImagesByDocumentId(String passportDocumentId) {
                return List.of();
            }

            @Override
            public List<QuizCandidate> findQuizEligibleCandidates() {
                return List.of();
            }
        };
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

    private record TrainingUi(
            Stage stage,
            NavigationService navigation,
            Parent modeRoot,
            Parent configurationRoot
    ) {
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
