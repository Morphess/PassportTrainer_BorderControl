package ua.passporttrainer.controller;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import ua.passporttrainer.map.MapGroup;
import ua.passporttrainer.map.MapInteractionState;
import ua.passporttrainer.map.MapRegion;
import ua.passporttrainer.map.WorldMapDefinition;
import ua.passporttrainer.map.WorldMapPane;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.EntityType;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.WorldMapCatalogue;
import ua.passporttrainer.service.CountryService;

/** JavaFX view-state adapter for map and fallback-list catalogue browsing. */
public final class WorldMapController {

    private final NavigationService navigationService;
    private final ResourceBundle resources;
    private final ResourceBundle regionLabels;
    private final WorldMapDefinition mapDefinition;
    private final WorldMapCatalogue mapCatalogue;
    private final ToggleGroup groupToggle = new ToggleGroup();
    private String activeGroupId;
    private WorldMapPane worldMapPane;

    @FXML
    private StackPane mapHost;
    @FXML
    private FlowPane groupButtons;
    @FXML
    private ListView<Country> entityList;
    @FXML
    private TextField searchField;
    @FXML
    private Label messageLabel;
    @FXML
    private Label selectionLabel;
    @FXML
    private Button openButton;

    public WorldMapController(
            NavigationService navigationService,
            CountryService countryService,
            WorldMapDefinition mapDefinition,
            ResourceBundle resources,
            ResourceBundle regionLabels
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.regionLabels = Objects.requireNonNull(regionLabels, "regionLabels");
        this.mapDefinition = Objects.requireNonNull(mapDefinition, "mapDefinition");
        mapCatalogue = new WorldMapCatalogue(mapDefinition, countryService);
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(mapHost, "mapHost was not injected");
        Objects.requireNonNull(groupButtons, "groupButtons was not injected");
        Objects.requireNonNull(entityList, "entityList was not injected");
        Objects.requireNonNull(searchField, "searchField was not injected");
        Objects.requireNonNull(messageLabel, "messageLabel was not injected");
        Objects.requireNonNull(selectionLabel, "selectionLabel was not injected");
        Objects.requireNonNull(openButton, "openButton was not injected");

        worldMapPane = new WorldMapPane(
                mapDefinition,
                new MapInteractionState(mapCatalogue.interactionData())
        );
        worldMapPane.setAccessibleText(resources.getString("map.accessible"));
        worldMapPane.setRegionLabelProvider(this::regionTooltip);
        worldMapPane.setOnRegionActivated(this::selectMapRegion);
        mapHost.getChildren().setAll(worldMapPane);

        configureGroupButtons();
        entityList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Country country, boolean empty) {
                super.updateItem(country, empty);
                setText(empty || country == null ? null : entityText(country));
            }
        });
        entityList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> updateSelection(selected)
        );
        entityList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedEntity();
            }
        });
        searchField.textProperty().addListener((observable, previous, query) -> applyFilter());
        openButton.disableProperty().bind(entityList.getSelectionModel().selectedItemProperty().isNull());
        applyFilter();
        showMessage("map.provisionalNotice", false);
    }

    @FXML
    private void zoomIn() {
        worldMapPane.zoomIn();
    }

    @FXML
    private void zoomOut() {
        worldMapPane.zoomOut();
    }

    @FXML
    private void resetMap() {
        groupToggle.selectToggle(null);
        activeGroupId = null;
        worldMapPane.resetView();
        applyFilter();
    }

    @FXML
    private void openSelectedEntity() {
        Country selected = entityList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            navigationService.show(
                    View.PASSPORTS,
                    PassportListController.class,
                    controller -> controller.showCountry(selected.id())
            );
        }
    }

    @FXML
    private void goBack() {
        navigationService.goBack();
    }

    private void configureGroupButtons() {
        for (MapGroup group : mapDefinition.orderedGroups()) {
            ToggleButton button = new ToggleButton(resources.getString(group.labelKey()));
            button.setId("mapGroup-" + group.id());
            button.setToggleGroup(groupToggle);
            button.getStyleClass().add("map-group-button");
            button.setAccessibleText(MessageFormat.format(
                    resources.getString("map.groupAccessible"),
                    resources.getString(group.labelKey()),
                    mapCatalogue.filter(group.id(), "").size()
            ));
            button.setOnAction(event -> {
                if (button.isSelected()) {
                    activeGroupId = group.id();
                    worldMapPane.focusGroup(group.id());
                } else {
                    activeGroupId = null;
                    worldMapPane.resetView();
                }
                applyFilter();
            });
            groupButtons.getChildren().add(button);
        }
    }

    private void selectMapRegion(MapRegion region) {
        Country country = mapCatalogue.countryForRegion(region.id()).orElseThrow();
        if (!entityList.getItems().contains(country)) {
            groupToggle.selectToggle(null);
            activeGroupId = null;
            worldMapPane.resetView();
            searchField.clear();
            applyFilter();
        }
        entityList.getSelectionModel().select(country);
        entityList.scrollTo(country);
    }

    private void updateSelection(Country selected) {
        if (selected == null) {
            worldMapPane.selectRegion(null);
            selectionLabel.setText(resources.getString("map.noSelection"));
            return;
        }
        worldMapPane.selectRegion(mapCatalogue.regionIdForCountry(selected.id()).orElse(null));
        selectionLabel.setText(MessageFormat.format(
                resources.getString("map.selection"), selected.displayName(), selected.documentCount()
        ));
    }

    private void applyFilter() {
        List<Country> filtered = mapCatalogue.filter(activeGroupId, searchField.getText());
        entityList.getItems().setAll(filtered);
        if (filtered.isEmpty()) {
            showMessage("map.noSearchResults", false);
        } else {
            showMessage("map.provisionalNotice", false);
        }
    }

    private String regionTooltip(MapRegion region) {
        String geographicName = regionLabels.getString(region.labelKey());
        return mapCatalogue.countryForRegion(region.id())
                .map(country -> MessageFormat.format(
                        resources.getString("map.tooltipLinked"),
                        geographicName,
                        country.displayName(),
                        country.documentCount(),
                        resources.getString("reviewStatus." + country.reviewStatus().name())
                ))
                .orElseGet(() -> MessageFormat.format(
                        resources.getString("map.tooltipUnlinked"), geographicName
                ));
    }

    private String entityText(Country country) {
        String status = resources.getString("reviewStatus." + country.reviewStatus().name());
        if (country.entityType() == EntityType.COUNTRY) {
            return MessageFormat.format(
                    resources.getString("map.entityItem"),
                    country.displayName(), country.documentCount(), status
            );
        }
        return MessageFormat.format(
                resources.getString("map.specialEntityItem"),
                country.displayName(),
                country.documentCount(),
                resources.getString("entityType." + country.entityType().name()),
                status
        );
    }

    private void showMessage(String key, boolean error) {
        messageLabel.setText(resources.getString(key));
        messageLabel.getStyleClass().remove("error-label");
        if (error) {
            messageLabel.getStyleClass().add("error-label");
        }
    }
}
