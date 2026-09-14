package ua.passporttrainer.controller;

import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.image.PassportImageLocator;
import ua.passporttrainer.navigation.NavigationControllerFactory;
import ua.passporttrainer.navigation.NavigationService;

import java.util.Objects;
import java.util.ResourceBundle;

/** Creates only the application's explicitly supported FXML controllers. */
public final class CatalogueControllerFactory implements NavigationControllerFactory {

    private final CatalogueApplicationContext catalogueContext;
    private final Throwable catalogueFailure;
    private final PassportImageLocator imageLocator;
    private final ResourceBundle resources;

    private CatalogueControllerFactory(
            CatalogueApplicationContext catalogueContext,
            Throwable catalogueFailure,
            PassportImageLocator imageLocator,
            ResourceBundle resources
    ) {
        this.catalogueContext = catalogueContext;
        this.catalogueFailure = catalogueFailure;
        this.imageLocator = Objects.requireNonNull(imageLocator, "imageLocator");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public static CatalogueControllerFactory available(
            CatalogueApplicationContext catalogueContext,
            PassportImageLocator imageLocator,
            ResourceBundle resources
    ) {
        return new CatalogueControllerFactory(
                Objects.requireNonNull(catalogueContext, "catalogueContext"), null, imageLocator, resources
        );
    }

    public static CatalogueControllerFactory unavailable(
            Throwable catalogueFailure,
            PassportImageLocator imageLocator,
            ResourceBundle resources
    ) {
        return new CatalogueControllerFactory(
                null, Objects.requireNonNull(catalogueFailure, "catalogueFailure"), imageLocator, resources
        );
    }

    @Override
    public Object create(Class<?> controllerType, NavigationService navigationService) {
        Objects.requireNonNull(controllerType, "controllerType");
        Objects.requireNonNull(navigationService, "navigationService");
        if (controllerType == StartController.class) {
            return new StartController(navigationService, catalogueContext != null, resources);
        }
        CatalogueApplicationContext context = requireCatalogue();
        if (controllerType == CatalogueController.class) {
            return new CatalogueController(
                    navigationService, context.continentService(), context.countryService(), resources
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
}
