package ua.passporttrainer.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import ua.passporttrainer.model.Continent;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.EntityType;
import ua.passporttrainer.navigation.NavigationService;
import ua.passporttrainer.navigation.View;
import ua.passporttrainer.presentation.CatalogueSearch;
import ua.passporttrainer.service.ContinentService;
import ua.passporttrainer.service.CountryService;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

/** Presentation controller for canonical continent and entity browsing. */
public final class CatalogueController {

    private final NavigationService navigationService;
    private final ContinentService continentService;
    private final CountryService countryService;
    private final ResourceBundle resources;
    private final Map<String, List<Country>> countriesByContinent = new LinkedHashMap<>();

    @FXML
    private ListView<Continent> continentList;
    @FXML
    private ListView<Country> entityList;
    @FXML
    private TextField searchField;
    @FXML
    private Label messageLabel;
    @FXML
    private Button openButton;

    public CatalogueController(
            NavigationService navigationService,
            ContinentService continentService,
            CountryService countryService,
            ResourceBundle resources
    ) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.continentService = Objects.requireNonNull(continentService, "continentService");
        this.countryService = Objects.requireNonNull(countryService, "countryService");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @FXML
    private void initialize() {
        Objects.requireNonNull(continentList, "continentList was not injected");
        Objects.requireNonNull(entityList, "entityList was not injected");
        Objects.requireNonNull(searchField, "searchField was not injected");
        Objects.requireNonNull(messageLabel, "messageLabel was not injected");
        Objects.requireNonNull(openButton, "openButton was not injected");

        continentList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Continent continent, boolean empty) {
                super.updateItem(continent, empty);
                setText(empty || continent == null ? null : continentText(continent));
            }
        });
        entityList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Country country, boolean empty) {
                super.updateItem(country, empty);
                setText(empty || country == null ? null : entityText(country));
            }
        });
        continentList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> updateEntities(selected)
        );
        searchField.textProperty().addListener((observable, previous, query) -> applyFilter());
        openButton.disableProperty().bind(entityList.getSelectionModel().selectedItemProperty().isNull());
        entityList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedEntity();
            }
        });
        loadCatalogue();
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

    private void loadCatalogue() {
        try {
            List<Continent> continents = continentService.findAll();
            for (Continent continent : continents) {
                countriesByContinent.put(continent.id(), countryService.findByContinentId(continent.id()));
            }
            continentList.getItems().setAll(continents);
            if (continents.isEmpty()) {
                showMessage("catalogue.emptyContinents", true);
            } else {
                showMessage("catalogue.provisionalNotice", false);
                continentList.getSelectionModel().selectFirst();
            }
        } catch (RuntimeException exception) {
            continentList.getItems().clear();
            entityList.getItems().clear();
            showMessage("error.catalogueLoad", true);
        }
    }

    private void updateEntities(Continent continent) {
        applyFilter();
        if (continent != null && countriesByContinent.getOrDefault(continent.id(), List.of()).isEmpty()) {
            showMessage("catalogue.emptyEntities", false);
        }
    }

    private void applyFilter() {
        Continent continent = continentList.getSelectionModel().getSelectedItem();
        List<Country> countries = continent == null
                ? List.of()
                : countriesByContinent.getOrDefault(continent.id(), List.of());
        List<Country> filtered = CatalogueSearch.filterAndSort(countries, searchField.getText());
        entityList.getItems().setAll(filtered);
        if (continent != null && !countries.isEmpty()) {
            showMessage(filtered.isEmpty() ? "catalogue.noSearchResults" : "catalogue.provisionalNotice", false);
        }
    }

    private String continentText(Continent continent) {
        int count = countriesByContinent.getOrDefault(continent.id(), List.of()).size();
        return MessageFormat.format(resources.getString("catalogue.continentItem"), continent.displayName(), count);
    }

    private String entityText(Country country) {
        String status = resources.getString("reviewStatus." + country.reviewStatus().name());
        if (country.entityType() == EntityType.COUNTRY) {
            return MessageFormat.format(resources.getString("catalogue.entityItem"), country.displayName(), status);
        }
        String type = resources.getString("entityType." + country.entityType().name());
        return MessageFormat.format(
                resources.getString("catalogue.specialEntityItem"), country.displayName(), type, status
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
