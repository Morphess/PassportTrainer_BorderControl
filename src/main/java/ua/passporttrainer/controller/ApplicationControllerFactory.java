package ua.passporttrainer.controller;

import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.map.MapDataException;
import ua.passporttrainer.map.WorldMapDefinition;
import ua.passporttrainer.map.WorldMapResourceLoader;
import ua.passporttrainer.map.WorldMapValidator;
import ua.passporttrainer.image.PassportImageLocator;
import ua.passporttrainer.navigation.NavigationControllerFactory;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.service.QuizSessionCoordinator;

import java.util.Objects;
import java.util.ResourceBundle;

/** Creates only the application's explicitly supported FXML controllers. */
public final class ApplicationControllerFactory implements NavigationControllerFactory {

    private final CatalogueApplicationContext catalogueContext;
    private final QuizSessionCoordinator sessionCoordinator;
    private final Throwable catalogueFailure;
    private final WorldMapDefinition mapDefinition;
    private final ResourceBundle regionLabels;
    private final Throwable mapFailure;
    private final PassportImageLocator imageLocator;
    private final ResourceBundle resources;

    private ApplicationControllerFactory(
            CatalogueApplicationContext catalogueContext,
            QuizSessionCoordinator sessionCoordinator,
            Throwable catalogueFailure,
            WorldMapDefinition mapDefinition,
            ResourceBundle regionLabels,
            Throwable mapFailure,
            PassportImageLocator imageLocator,
            ResourceBundle resources
    ) {
        this.catalogueContext = catalogueContext;
        this.sessionCoordinator = sessionCoordinator;
        this.catalogueFailure = catalogueFailure;
        this.mapDefinition = mapDefinition;
        this.regionLabels = regionLabels;
        this.mapFailure = mapFailure;
        this.imageLocator = Objects.requireNonNull(imageLocator, "imageLocator");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public static ApplicationControllerFactory available(
            CatalogueApplicationContext catalogueContext,
            QuizSessionCoordinator sessionCoordinator,
            PassportImageLocator imageLocator,
            ResourceBundle resources
    ) {
        Objects.requireNonNull(catalogueContext, "catalogueContext");
        Objects.requireNonNull(sessionCoordinator, "sessionCoordinator");
        WorldMapDefinition mapDefinition = null;
        ResourceBundle regionLabels = null;
        Throwable mapFailure = null;
        try {
            mapDefinition = WorldMapResourceLoader.loadDefault();
            regionLabels = ResourceBundle.getBundle("i18n.map-regions");
            WorldMapValidator.validate(
                    mapDefinition,
                    catalogueContext.countryService(),
                    catalogueContext.continentService(),
                    resources,
                    regionLabels
            );
        } catch (RuntimeException exception) {
            mapFailure = exception;
        }
        return new ApplicationControllerFactory(
                catalogueContext, sessionCoordinator, null,
                mapDefinition, regionLabels, mapFailure, imageLocator, resources
        );
    }

    public static ApplicationControllerFactory unavailable(
            Throwable catalogueFailure,
            PassportImageLocator imageLocator,
            ResourceBundle resources
    ) {
        Throwable failure = Objects.requireNonNull(catalogueFailure, "catalogueFailure");
        return new ApplicationControllerFactory(
                null, null, failure, null, null, failure, imageLocator, resources
        );
    }

    @Override
    public Object create(Class<?> controllerType, NavigationService navigationService) {
        Objects.requireNonNull(controllerType, "controllerType");
        Objects.requireNonNull(navigationService, "navigationService");
        if (controllerType == StartController.class) {
            return new StartController(
                    navigationService, catalogueContext != null, mapDefinition != null && mapFailure == null, resources
            );
        }
        CatalogueApplicationContext context = requireCatalogue();
        if (controllerType == CatalogueController.class) {
            return new CatalogueController(
                    navigationService, context.continentService(), context.countryService(), resources
            );
        }
        if (controllerType == WorldMapController.class) {
            requireMap();
            return new WorldMapController(
                    navigationService,
                    context.countryService(),
                    mapDefinition,
                    resources,
                    regionLabels
            );
        }
        if (controllerType == PassportListController.class) {
            return new PassportListController(
                    navigationService, context.countryService(), context.passportService(), resources
            );
        }
        if (controllerType == PassportDetailsController.class) {
            return new PassportDetailsController(
                    navigationService, context.passportService(), imageLocator, resources
            );
        }
        throw new IllegalArgumentException("Unsupported FXML controller: " + controllerType.getName());
    }

    private CatalogueApplicationContext requireCatalogue() {
        if (catalogueContext == null) {
            throw new IllegalStateException("Catalogue is unavailable", catalogueFailure);
        }
        return catalogueContext;
    }

    private void requireMap() {
        if (mapDefinition == null || regionLabels == null || mapFailure != null) {
            throw new MapDataException("World map is unavailable", mapFailure);
        }
    }
}
