package ua.passporttrainer.map;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.CatalogueTestSupport;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.model.Country;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapValidatorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void pinnedMetadataExactlyAndUniquelyCoversCanonicalCatalogue() throws Exception {
        CatalogueApplicationContext catalogue = CatalogueApplicationContext.create(
                CatalogueTestSupport.importCatalogue(tempDirectory.resolve("catalogue.db"))
        );
        WorldMapDefinition definition = WorldMapResourceLoader.loadDefault();

        WorldMapValidator.validate(
                definition,
                catalogue.countryService(),
                catalogue.continentService(),
                ResourceBundle.getBundle("i18n.messages"),
                ResourceBundle.getBundle("i18n.map-regions")
        );

        assertEquals(249, definition.regions().size());
        assertEquals(135, definition.regions().values().stream().filter(MapRegion::linked).count());
        assertEquals(114, definition.regions().values().stream().filter(region -> !region.linked()).count());
        assertEquals(139, catalogue.countryService().findAll().size());
        assertEquals(4, definition.entityExclusions().size());
        assertEquals(
                139,
                definition.regions().values().stream()
                        .filter(MapRegion::linked)
                        .map(MapRegion::canonicalEntityId)
                        .collect(Collectors.toSet()).size()
                        + definition.entityExclusions().size()
        );
    }

    @Test
    void highRiskMappingsUseExplicitCanonicalEntitiesAndListOnlyReasons() throws Exception {
        CatalogueApplicationContext catalogue = CatalogueApplicationContext.create(
                CatalogueTestSupport.importCatalogue(tempDirectory.resolve("high-risk.db"))
        );
        WorldMapDefinition definition = WorldMapResourceLoader.loadDefault();
        Map<String, Country> countriesByName = catalogue.countryService().findAll().stream()
                .collect(Collectors.toMap(Country::displayName, Function.identity()));

        assertRegionMapsTo(definition, "UKR", countriesByName.get("Україна"));
        assertRegionMapsTo(definition, "CYP", countriesByName.get("Кіпр"));
        assertRegionMapsTo(definition, "TWN", countriesByName.get("Тайвань"));
        assertRegionMapsTo(definition, "TLS", countriesByName.get("Тімор-Лешті"));
        assertRegionMapsTo(definition, "BMU", countriesByName.get("Бермуди"));
        assertRegionMapsTo(definition, "MAC", countriesByName.get("Макао"));

        assertListOnly(
                definition,
                countriesByName.get("Північний Кіпр"),
                "NO_GEOMETRY_IN_UKRAINE_POV"
        );
        assertListOnly(
                definition,
                countriesByName.get("Сомаліленд"),
                "NO_GEOMETRY_IN_UKRAINE_POV"
        );
        assertListOnly(
                definition,
                countriesByName.get("Корея (неуточнено)"),
                "AMBIGUOUS_IDENTITY"
        );
        assertListOnly(
                definition,
                countriesByName.get("Група Африканського банку розвитку"),
                "NON_GEOGRAPHIC_ORGANIZATION"
        );
    }
    @Test
    void rejectsRegionLinkToUnknownCanonicalEntity() throws Exception {
        CatalogueApplicationContext catalogue = CatalogueApplicationContext.create(
                CatalogueTestSupport.importCatalogue(tempDirectory.resolve("invalid-map.db"))
        );
        WorldMapDefinition valid = WorldMapResourceLoader.loadDefault();
        Map<String, MapRegion> regions = new LinkedHashMap<>(valid.regions());
        MapRegion ukraine = regions.get("UKR");
        regions.put("UKR", new MapRegion(
                ukraine.id(),
                ukraine.pathData(),
                ukraine.evenOddFill(),
                ukraine.mapGroupId(),
                MapLinkStatus.LINKED,
                "entity:missing",
                ukraine.labelKey(),
                ""
        ));
        WorldMapDefinition invalid = new WorldMapDefinition(
                valid.bounds(),
                valid.groups(),
                regions,
                valid.entityExclusions(),
                valid.assetMetadata()
        );

        MapDataException exception = assertThrows(MapDataException.class, () ->
                WorldMapValidator.validate(
                        invalid,
                        catalogue.countryService(),
                        catalogue.continentService(),
                        ResourceBundle.getBundle("i18n.messages"),
                        ResourceBundle.getBundle("i18n.map-regions")
                )
        );

        assertTrue(exception.getMessage().contains(
                "map region UKR references unknown entity entity:missing"
        ));
    }


    private static void assertRegionMapsTo(
            WorldMapDefinition definition,
            String regionId,
            Country expectedCountry
    ) {
        MapRegion region = definition.regions().get(regionId);
        assertSame(MapLinkStatus.LINKED, region.linkStatus());
        assertEquals(expectedCountry.id(), region.canonicalEntityId());
        assertFalse(definition.entityExclusions().containsKey(expectedCountry.id()));
    }

    private static void assertListOnly(
            WorldMapDefinition definition,
            Country country,
            String expectedReason
    ) {
        MapEntityExclusion exclusion = definition.entityExclusions().get(country.id());
        assertEquals(expectedReason, exclusion.reasonCode());
        assertFalse(definition.regions().values().stream()
                .anyMatch(region -> country.id().equals(region.canonicalEntityId())));
    }

}
