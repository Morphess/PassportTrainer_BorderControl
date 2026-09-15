package ua.passporttrainer.presentation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import ua.passporttrainer.map.MapGroup;
import ua.passporttrainer.map.MapInteractionState;
import ua.passporttrainer.map.MapRegion;
import ua.passporttrainer.map.WorldMapDefinition;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.ReviewStatus;
import ua.passporttrainer.service.CountryService;

/** Immutable presentation data joining validated map regions with canonical catalogue entities. */
public final class WorldMapCatalogue {

    private final WorldMapDefinition definition;
    private final Map<String, Country> countriesById;
    private final Map<String, Country> countriesByRegionId;
    private final Map<String, String> regionIdsByCountryId;
    private final Map<String, List<Country>> countriesByGroupId;
    private final List<Country> allCountries;

    public WorldMapCatalogue(WorldMapDefinition definition, CountryService countryService) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(countryService, "countryService");
        allCountries = CatalogueSearch.filterAndSort(countryService.findAll(), "");
        Map<String, Country> indexedCountries = new LinkedHashMap<>();
        allCountries.forEach(country -> indexedCountries.put(country.id(), country));
        countriesById = Map.copyOf(indexedCountries);

        Map<String, Country> regionCountries = new LinkedHashMap<>();
        Map<String, String> countryRegions = new LinkedHashMap<>();
        for (MapRegion region : definition.regions().values()) {
            if (region.linked()) {
                Country country = countriesById.get(region.canonicalEntityId());
                if (country == null) {
                    throw new IllegalArgumentException("Validated map references unavailable country "
                            + region.canonicalEntityId());
                }
                regionCountries.put(region.id(), country);
                countryRegions.put(country.id(), region.id());
            }
        }
        countriesByRegionId = Map.copyOf(regionCountries);
        regionIdsByCountryId = Map.copyOf(countryRegions);

        Map<String, List<Country>> grouped = new LinkedHashMap<>();
        for (MapGroup group : definition.groups().values()) {
            Map<String, Country> unique = new LinkedHashMap<>();
            for (String continentId : group.canonicalContinentIds()) {
                countryService.findByContinentId(continentId)
                        .forEach(country -> unique.putIfAbsent(country.id(), country));
            }
            grouped.put(group.id(), CatalogueSearch.filterAndSort(unique.values(), ""));
        }
        countriesByGroupId = Map.copyOf(grouped);
    }

    public List<Country> filter(String groupId, String query) {
        Collection<Country> scope = groupId == null
                ? allCountries
                : countriesByGroupId.getOrDefault(groupId, List.of());
        return CatalogueSearch.filterAndSort(scope, query);
    }

    public Optional<Country> countryForRegion(String regionId) {
        return Optional.ofNullable(countriesByRegionId.get(regionId));
    }

    public Optional<String> regionIdForCountry(String countryId) {
        return Optional.ofNullable(regionIdsByCountryId.get(countryId));
    }

    public Map<String, MapInteractionState.RegionData> interactionData() {
        Map<String, MapInteractionState.RegionData> data = new LinkedHashMap<>();
        for (MapRegion region : definition.regions().values()) {
            Country country = countriesByRegionId.get(region.id());
            data.put(region.id(), new MapInteractionState.RegionData(
                    country != null,
                    country == null ? 0 : country.documentCount(),
                    country != null && country.reviewStatus() == ReviewStatus.NEEDS_REVIEW
            ));
        }
        return Map.copyOf(data);
    }
}
