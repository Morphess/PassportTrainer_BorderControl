package ua.passporttrainer.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import ua.passporttrainer.model.Continent;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.EntityType;
import ua.passporttrainer.service.ContinentService;
import ua.passporttrainer.service.CountryService;

/** Enforces the complete, explicit relationship between map features and canonical entities. */
public final class WorldMapValidator {

    public static final int EXPECTED_REGION_COUNT = 249;
    public static final int EXPECTED_LINKED_REGION_COUNT = 135;
    public static final int EXPECTED_UNLINKED_REGION_COUNT = 114;
    public static final int EXPECTED_CANONICAL_ENTITY_COUNT = 139;
    public static final int EXPECTED_LIST_ONLY_ENTITY_COUNT = 4;

    private static final Map<String, List<String>> EXPECTED_GROUP_CONTINENTS = Map.of(
            "EUROPE", List.of("continent:europe"),
            "ASIA", List.of("continent:asia"),
            "AFRICA", List.of("continent:africa"),
            "NORTH_AMERICA", List.of("continent:north-america"),
            "SOUTH_AMERICA", List.of("continent:south-america"),
            "AUSTRALIA_OCEANIA", List.of("continent:australia", "continent:oceania")
    );

    private WorldMapValidator() {
    }

    public static void validate(
            WorldMapDefinition definition,
            CountryService countryService,
            ContinentService continentService,
            ResourceBundle messages,
            ResourceBundle regionLabels
    ) {
        List<String> errors = new ArrayList<>();
        validatePinnedAsset(definition, errors);
        validateGroups(definition, continentService.findAll(), messages, errors);

        List<Country> countries = countryService.findAll();
        Map<String, Country> countriesById = uniqueIndex(countries, Country::id, "country", errors);
        Map<String, Set<String>> continentIdsByEntity = buildContinentMemberships(
                countriesById.keySet(), continentService.findAll(), countryService, errors
        );
        validateRegions(
                definition, countriesById, continentIdsByEntity, regionLabels, errors
        );

        if (!errors.isEmpty()) {
            throw new MapDataException("World-map validation failed:\n - " + String.join("\n - ", errors));
        }
    }

    private static void validatePinnedAsset(WorldMapDefinition definition, List<String> errors) {
        MapAssetMetadata asset = definition.assetMetadata();
        requireEqual("ne_10m_admin_0_countries_ukr", asset.dataset(), "source dataset", errors);
        requireEqual("5.1.1", asset.version(), "source version", errors);
        requireEqual("v5.1.2", asset.tag(), "source tag", errors);
        requireEqual("UKRAINE", asset.viewpoint(), "source viewpoint", errors);
        requireEqual(EXPECTED_REGION_COUNT, asset.regionCount(), "asset region count", errors);
        requireEqual(EXPECTED_REGION_COUNT, definition.regions().size(), "loaded region count", errors);
    }

    private static void validateGroups(
            WorldMapDefinition definition,
            List<Continent> continents,
            ResourceBundle messages,
            List<String> errors
    ) {
        if (!definition.groups().keySet().equals(EXPECTED_GROUP_CONTINENTS.keySet())) {
            errors.add("map groups differ from the required six presentation groups");
        }
        Set<String> canonicalContinentIds = continents.stream()
                .map(Continent::id)
                .collect(Collectors.toSet());
        for (MapGroup group : definition.groups().values()) {
            List<String> expectedContinents = EXPECTED_GROUP_CONTINENTS.get(group.id());
            if (expectedContinents != null && !group.canonicalContinentIds().equals(expectedContinents)) {
                errors.add("map group " + group.id() + " has unexpected canonical continents");
            }
            if (!canonicalContinentIds.containsAll(group.canonicalContinentIds())) {
                errors.add("map group " + group.id() + " references an unknown canonical continent");
            }
            if (!messages.containsKey(group.labelKey())) {
                errors.add("missing localized map group label: " + group.labelKey());
            }
        }
        MapGroup oceania = definition.groups().get("AUSTRALIA_OCEANIA");
        if (oceania != null && !oceania.wrapsDateline()) {
            errors.add("AUSTRALIA_OCEANIA must declare dateline wrapping");
        }
    }

    private static Map<String, Set<String>> buildContinentMemberships(
            Set<String> entityIds,
            List<Continent> continents,
            CountryService countryService,
            List<String> errors
    ) {
        Map<String, Set<String>> continentIdsByEntity = new HashMap<>();
        entityIds.forEach(id -> continentIdsByEntity.put(id, new LinkedHashSet<>()));
        for (Continent continent : continents) {
            for (Country country : countryService.findByContinentId(continent.id())) {
                Set<String> memberships = continentIdsByEntity.get(country.id());
                if (memberships == null) {
                    errors.add("continent lookup returned unknown entity " + country.id());
                } else {
                    memberships.add(continent.id());
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : continentIdsByEntity.entrySet()) {
            if (entry.getValue().isEmpty()) {
                errors.add("canonical entity has no continent membership: " + entry.getKey());
            }
        }
        return continentIdsByEntity;
    }

    private static void validateRegions(
            WorldMapDefinition definition,
            Map<String, Country> countriesById,
            Map<String, Set<String>> continentIdsByEntity,
            ResourceBundle regionLabels,
            List<String> errors
    ) {
        int linkedCount = 0;
        int unlinkedCount = 0;
        Set<String> linkedEntityIds = new HashSet<>();
        for (MapRegion region : definition.regions().values()) {
            if (!regionLabels.containsKey(region.labelKey())) {
                errors.add("missing localized region label: " + region.labelKey());
            }
            if (region.linked()) {
                linkedCount++;
                Country country = countriesById.get(region.canonicalEntityId());
                if (country == null) {
                    errors.add("map region " + region.id() + " references unknown entity "
                            + region.canonicalEntityId());
                    continue;
                }
                if (!linkedEntityIds.add(country.id())) {
                    errors.add("canonical entity is linked to multiple map regions: " + country.id());
                }
                if (country.entityType() == EntityType.ORGANIZATION
                        || country.entityType() == EntityType.AMBIGUOUS_ENTITY) {
                    errors.add("non-geographic entity is linked to map region " + region.id());
                }
                MapGroup group = definition.groups().get(region.mapGroupId());
                if (group == null) {
                    errors.add("linked map region " + region.id() + " references unknown map group "
                            + region.mapGroupId());
                } else {
                    Set<String> memberships = continentIdsByEntity.getOrDefault(country.id(), Set.of());
                    if (group.canonicalContinentIds().stream().noneMatch(memberships::contains)) {
                        errors.add("map region " + region.id() + " group conflicts with entity continents");
                    }
                }
            } else {
                unlinkedCount++;
                if (!"NONE".equals(region.mapGroupId())
                        && !definition.groups().containsKey(region.mapGroupId())) {
                    errors.add("unlinked map region " + region.id() + " references unknown map group "
                            + region.mapGroupId());
                }
            }
        }
        requireEqual(EXPECTED_LINKED_REGION_COUNT, linkedCount, "linked region count", errors);
        requireEqual(EXPECTED_UNLINKED_REGION_COUNT, unlinkedCount, "unlinked region count", errors);

        Set<String> listOnlyEntityIds = definition.entityExclusions().keySet();
        requireEqual(EXPECTED_LIST_ONLY_ENTITY_COUNT, listOnlyEntityIds.size(),
                "list-only entity count", errors);
        for (MapEntityExclusion exclusion : definition.entityExclusions().values()) {
            if (!countriesById.containsKey(exclusion.canonicalEntityId())) {
                errors.add("list-only metadata references unknown entity " + exclusion.canonicalEntityId());
            }
            if (linkedEntityIds.contains(exclusion.canonicalEntityId())) {
                errors.add("entity is both map-linked and list-only: " + exclusion.canonicalEntityId());
            }
        }

        Set<String> representedEntityIds = new HashSet<>(linkedEntityIds);
        representedEntityIds.addAll(listOnlyEntityIds);
        requireEqual(EXPECTED_CANONICAL_ENTITY_COUNT, countriesById.size(),
                "canonical entity count", errors);
        if (!representedEntityIds.equals(countriesById.keySet())) {
            Set<String> missing = new HashSet<>(countriesById.keySet());
            missing.removeAll(representedEntityIds);
            Set<String> unknown = new HashSet<>(representedEntityIds);
            unknown.removeAll(countriesById.keySet());
            errors.add("canonical map coverage mismatch; missing=" + missing + ", unknown=" + unknown);
        }
    }

    private static <T> Map<String, T> uniqueIndex(
            List<T> values,
            Function<T, String> keyFunction,
            String kind,
            List<String> errors
    ) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (T value : values) {
            String key = keyFunction.apply(value);
            if (indexed.putIfAbsent(key, value) != null) {
                errors.add("duplicate " + kind + " id: " + key);
            }
        }
        return indexed;
    }

    private static void requireEqual(Object expected, Object actual, String subject, List<String> errors) {
        if (!expected.equals(actual)) {
            errors.add(subject + " expected " + expected + " but was " + actual);
        }
    }
}
