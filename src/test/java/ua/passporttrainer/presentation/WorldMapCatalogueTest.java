package ua.passporttrainer.presentation;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.CatalogueTestSupport;
import ua.passporttrainer.catalogue.CatalogueApplicationContext;
import ua.passporttrainer.map.WorldMapDefinition;
import ua.passporttrainer.map.WorldMapResourceLoader;
import ua.passporttrainer.model.Country;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapCatalogueTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void combinesAustraliaAndOceaniaWithoutDuplicatesAndRetainsListOnlyEntities() throws Exception {
        WorldMapCatalogue catalogue = catalogue();

        List<Country> australiaOceania = catalogue.filter("AUSTRALIA_OCEANIA", "");
        assertEquals(1, australiaOceania.stream()
                .filter(country -> country.displayName().equals("Тімор-Лешті"))
                .count());
        assertTrue(australiaOceania.stream()
                .anyMatch(country -> country.displayName().equals("Австралія")));
        assertEquals("Північний Кіпр", only(catalogue.filter("ASIA", "північний кіпр"))
                .displayName());
        assertEquals("Сомаліленд", only(catalogue.filter("AFRICA", "сомаліленд"))
                .displayName());
        assertEquals("Група Африканського банку розвитку", only(catalogue
                .filter("AFRICA", "африканського банку"))
                .displayName());
    }

    @Test
    void UnicodeSearchIsCaseInsensitiveAndNormalizesCanonicalNames() throws Exception {
        WorldMapCatalogue catalogue = catalogue();
        String decomposedMixedCase = Normalizer.normalize("уКрАїНа", Normalizer.Form.NFD);

        assertEquals("Україна", only(catalogue.filter(null, decomposedMixedCase)).displayName());
    }

    @Test
    void explicitRegionLinksNeverGuessAmbiguousOrListOnlyEntities() throws Exception {
        WorldMapCatalogue catalogue = catalogue();

        assertEquals("Кіпр", catalogue.countryForRegion("CYP").orElseThrow().displayName());
        assertTrue(catalogue.countryForRegion("KOR").isEmpty());
        Country ambiguousKorea = only(catalogue.filter("ASIA", "корея (неуточнено)"));
        assertEquals("Корея (неуточнено)", ambiguousKorea.displayName());
        assertTrue(catalogue.regionIdForCountry(ambiguousKorea.id()).isEmpty());
    }

    private static Country only(List<Country> countries) {
        assertEquals(1, countries.size());
        return countries.getFirst();
    }

    private WorldMapCatalogue catalogue() throws Exception {
        CatalogueTestSupport.importCatalogue(temporaryDirectory.resolve("map-catalogue.sqlite"));
        CatalogueApplicationContext context = CatalogueApplicationContext.openReadOnly(
                temporaryDirectory.resolve("map-catalogue.sqlite")
        );
        WorldMapDefinition definition = WorldMapResourceLoader.loadDefault();
        return new WorldMapCatalogue(definition, context.countryService());
    }
}
