package ua.passporttrainer.presentation;

import ua.passporttrainer.model.Country;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Pure presentation filtering and ordering for canonical entity names. */
public final class CatalogueSearch {

    private static final Comparator<Country> DISPLAY_ORDER = Comparator
            .comparing((Country country) -> searchKey(country.displayName()))
            .thenComparing(Country::id);

    private CatalogueSearch() {
    }

    public static List<Country> filterAndSort(Collection<Country> countries, String query) {
        Objects.requireNonNull(countries, "countries");
        String queryKey = searchKey(query == null ? "" : query);
        return countries.stream()
                .filter(country -> searchKey(country.displayName()).contains(queryKey))
                .sorted(DISPLAY_ORDER)
                .toList();
    }

    private static String searchKey(String value) {
        return Normalizer.normalize(Objects.requireNonNull(value, "value"), Normalizer.Form.NFC)
                .strip()
                .toLowerCase(Locale.ROOT);
    }
}
