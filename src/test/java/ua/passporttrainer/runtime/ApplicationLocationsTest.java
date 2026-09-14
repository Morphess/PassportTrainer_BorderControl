package ua.passporttrainer.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock("system-properties")
class ApplicationLocationsTest {

    @Test
    void usesRepositoryRelativeDevelopmentDefaults() {
        withProperties(null, null, () -> {
            ApplicationLocations locations = ApplicationLocations.fromSystemProperties();

            assertEquals(
                    Path.of("data", "passport-catalogue", "passport-catalogue.sqlite").toAbsolutePath().normalize(),
                    locations.cataloguePath()
            );
            assertEquals(
                    Path.of("Зразки паспортних документів").toAbsolutePath().normalize(),
                    locations.passportAssetsRoot()
            );
        });
    }

    @Test
    void systemPropertiesOverrideBothExternalLocations() {
        Path catalogue = Path.of("target", "configured-catalogue.sqlite");
        Path assets = Path.of("target", "configured-passport-assets");

        withProperties(catalogue.toString(), assets.toString(), () -> {
            ApplicationLocations locations = ApplicationLocations.fromSystemProperties();

            assertEquals(catalogue.toAbsolutePath().normalize(), locations.cataloguePath());
            assertEquals(assets.toAbsolutePath().normalize(), locations.passportAssetsRoot());
        });
    }

    private static void withProperties(String catalogue, String assets, Runnable assertion) {
        String previousCatalogue = System.getProperty(ApplicationLocations.CATALOGUE_PATH_PROPERTY);
        String previousAssets = System.getProperty(ApplicationLocations.ASSETS_ROOT_PROPERTY);
        try {
            setOrClear(ApplicationLocations.CATALOGUE_PATH_PROPERTY, catalogue);
            setOrClear(ApplicationLocations.ASSETS_ROOT_PROPERTY, assets);
            assertion.run();
        } finally {
            setOrClear(ApplicationLocations.CATALOGUE_PATH_PROPERTY, previousCatalogue);
            setOrClear(ApplicationLocations.ASSETS_ROOT_PROPERTY, previousAssets);
        }
    }

    private static void setOrClear(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}
