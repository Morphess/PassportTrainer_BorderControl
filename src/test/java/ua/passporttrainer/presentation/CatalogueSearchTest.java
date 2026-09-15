package ua.passporttrainer.presentation;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.Country;
import ua.passporttrainer.model.EntityType;
import ua.passporttrainer.model.ReviewStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogueSearchTest {

    @Test
    void matchesCanonicalNamesCaseInsensitivelyAcrossUnicodeNormalizationForms() {
        Country ukraine = country("entity:ukraine", "Україна");
        Country seychelles = country("entity:seychelles", "Сейшельські Острови");

        assertEquals(List.of(ukraine), CatalogueSearch.filterAndSort(List.of(seychelles, ukraine), "УКРАЇНА"));
        assertEquals(List.of(seychelles), CatalogueSearch.filterAndSort(List.of(ukraine, seychelles), "сейшель"));
    }

    @Test
    void returnsAStableCanonicalNameAndIdOrder() {
        Country second = country("entity:second", "Бета");
        Country first = country("entity:first", "Альфа");
        Country duplicateNameA = country("entity:a", "Гамма");
        Country duplicateNameB = country("entity:b", "Гамма");

        assertEquals(
                List.of(first, second, duplicateNameA, duplicateNameB),
                CatalogueSearch.filterAndSort(List.of(duplicateNameB, second, duplicateNameA, first), "")
        );
    }

    @Test
    void searchesCanonicalDisplayNamesOnly() {
        Country country = new Country(
                "entity:ukraine",
                "continent:europe",
                null,
                null,
                "Україна",
                "Ukraine source directory",
                EntityType.COUNTRY,
                ReviewStatus.AUTO_CLASSIFIED,
                1
        );

        assertEquals(List.of(), CatalogueSearch.filterAndSort(List.of(country), "source directory"));
    }

    private static Country country(String id, String displayName) {
        return new Country(
                id,
                "continent:europe",
                null,
                null,
                displayName,
                displayName,
                EntityType.COUNTRY,
                ReviewStatus.AUTO_CLASSIFIED,
                0
        );
    }
}
