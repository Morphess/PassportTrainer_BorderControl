package ua.passporttrainer.controller;

import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.image.PassportImageLocator;
import ua.passporttrainer.map.MapDataException;
import ua.passporttrainer.map.WorldMapDefinition;
import ua.passporttrainer.map.WorldMapResourceLoader;
import ua.passporttrainer.map.WorldMapValidator;
import ua.passporttrainer.navigation.NavigationControllerFactory;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.presentation.QuestionPresentationMapper;
import ua.passporttrainer.presentation.QuizGenerationFailureFormatter;
import ua.passporttrainer.service.ExamResultService;
import ua.passporttrainer.service.ExaminationConfigurationFactory;
import ua.passporttrainer.service.QuizSessionCoordinator;
import ua.passporttrainer.service.TrainingConfigurationFactory;

import java.util.Objects;
import java.util.ResourceBundle;

/** Creates only the application's explicitly supported FXML controllers. */
public final class ApplicationControllerFactory implements NavigationControllerFactory {

    private final CatalogueApplicationContext catalogueContext;
    private final QuizSessionCoordinator sessionCoordinator;
    private final ExamResultService examResultService;
    private final Throwable catalogueFailure;
    private final WorldMapDefinition mapDefinition;
    private final ResourceBundle regionLabels;
    private final Throwable mapFailure;
    private final PassportImageLocator imageLocator;
    private final ResourceBundle resources;
    private final SessionAbandonmentConfirmation abandonmentConfirmation;
    private final TrainingConfigurationFactory trainingConfigurationFactory;
    private final QuestionPresentationMapper questionPresentationMapper;
    private final QuizGenerationFailureFormatter trainingFailureFormatter;
    private final QuizGenerationFailureFormatter examinationFailureFormatter;
    private final ExaminationConfigurationFactory examinationConfigurationFactory;

    private ApplicationControllerFactory(
            CatalogueApplicationContext catalogueContext,
            QuizSessionCoordinator sessionCoordinator,
            ExamResultService examResultService,
            Throwable catalogueFailure,
            WorldMapDefinition mapDefinition,
            ResourceBundle regionLabels,
            Throwable mapFailure,
            PassportImageLocator imageLocator,
            SessionAbandonmentConfirmation abandonmentConfirmation,
            ResourceBundle resources
    ) {
        this.catalogueContext = catalogueContext;
        this.sessionCoordinator = sessionCoordinator;
        this.examResultService = examResultService;
        this.catalogueFailure = catalogueFailure;
        this.mapDefinition = mapDefinition;
        this.regionLabels = regionLabels;
        this.mapFailure = mapFailure;
        this.imageLocator = Objects.requireNonNull(imageLocator, "imageLocator");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.abandonmentConfirmation = abandonmentConfirmation;
        this.trainingConfigurationFactory = new TrainingConfigurationFactory();
        this.questionPresentationMapper = new QuestionPresentationMapper(resources);
        this.trainingFailureFormatter = new QuizGenerationFailureFormatter(
                resources, QuizMode.TRAINING
        );
        this.examinationFailureFormatter = new QuizGenerationFailureFormatter(
                resources, QuizMode.EXAMINATION
        );
        this.examinationConfigurationFactory = new ExaminationConfigurationFactory();
    }

    public static ApplicationControllerFactory available(
            CatalogueApplicationContext catalogueContext,
            QuizSessionCoordinator sessionCoordinator,
            ExamResultService examResultService,
            PassportImageLocator imageLocator,
            SessionAbandonmentConfirmation abandonmentConfirmation,
            ResourceBundle resources
    ) {
        Objects.requireNonNull(catalogueContext, "catalogueContext");
        Objects.requireNonNull(sessionCoordinator, "sessionCoordinator");
        Objects.requireNonNull(examResultService, "examResultService");
        Objects.requireNonNull(abandonmentConfirmation, "abandonmentConfirmation");
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
                catalogueContext, sessionCoordinator, examResultService, null,
                mapDefinition, regionLabels, mapFailure, imageLocator,
                abandonmentConfirmation, resources
        );
    }

    public static ApplicationControllerFactory unavailable(
            Throwable catalogueFailure,
            PassportImageLocator imageLocator,
            ResourceBundle resources
    ) {
        Throwable failure = Objects.requireNonNull(catalogueFailure, "catalogueFailure");
        return new ApplicationControllerFactory(
                null, null, null, failure, null, null, failure, imageLocator, null, resources
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
        if (controllerType == ModeSelectionController.class) {
            return new ModeSelectionController(navigationService);
        }
        if (controllerType == LearningEntitySelectionController.class) {
            return new LearningEntitySelectionController(
                    navigationService, context.learningService(), resources
            );
        }
        if (controllerType == LearningBrowserController.class) {
            return new LearningBrowserController(
                    navigationService, context.learningService(), imageLocator, resources
            );
        }
        if (controllerType == TrainingConfigurationController.class) {
            return new TrainingConfigurationController(
                    navigationService,
                    sessionCoordinator,
                    context.learningService(),
                    trainingConfigurationFactory,
                    trainingFailureFormatter,
                    resources
            );
        }
        if (controllerType == ExaminationConfirmationController.class) {
            return new ExaminationConfirmationController(
                    navigationService,
                    sessionCoordinator,
                    examinationConfigurationFactory,
                    examinationFailureFormatter,
                    resources
            );
        }
        if (controllerType == QuestionController.class) {
            return new QuestionController(
                    navigationService,
                    sessionCoordinator,
                    questionPresentationMapper,
                    imageLocator,
                    abandonmentConfirmation,
                    resources
            );
        }
        if (controllerType == TrainingResultController.class) {
            return new TrainingResultController(
                    navigationService, sessionCoordinator, trainingFailureFormatter, resources
            );
        }
        if (controllerType == ExaminationResultController.class) {
            return new ExaminationResultController(
                    navigationService, sessionCoordinator, examResultService, resources
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
