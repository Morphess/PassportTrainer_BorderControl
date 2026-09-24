package ua.passporttrainer.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock("system-properties")
class ApplicationLocationsTest {

    @Test
    void usesRepositoryRelativeDevelopmentDefaults() {
        withProperties(null, null, null, () -> {
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
    void systemPropertiesOverrideAllExternalLocations() {
        Path catalogue = Path.of("target", "configured-catalogue.sqlite");
        Path assets = Path.of("target", "configured-passport-assets");
        Path applicationDatabase = Path.of("target", "configured-application.sqlite");

        withProperties(
                catalogue.toString(),
                assets.toString(),
                applicationDatabase.toString(),
                () -> {
                    ApplicationLocations locations = ApplicationLocations.fromSystemProperties();

                    assertEquals(
                            catalogue.toAbsolutePath().normalize(),
                            locations.cataloguePath()
                    );
                    assertEquals(
                            assets.toAbsolutePath().normalize(),
                            locations.passportAssetsRoot()
                    );
                    assertEquals(
                            applicationDatabase.toAbsolutePath().normalize(),
                            locations.applicationDatabasePath()
                    );
                }
        );
    }

    @Test
    void usesPlatformUserDataDirectoriesForApplicationDatabase() {
        Path home = Path.of("home");

        assertEquals(
                Path.of("local", "PassportTrainer", "passport-trainer.sqlite"),
                ApplicationLocations.defaultApplicationDatabasePath(
                        "Windows 11", home, "local", null
                )
        );
        assertEquals(
                Path.of("xdg", "passport-trainer", "passport-trainer.sqlite"),
                ApplicationLocations.defaultApplicationDatabasePath(
                        "Linux", home, null, "xdg"
                )
        );
        assertEquals(
                Path.of("home", ".local", "share", "passport-trainer",
                        "passport-trainer.sqlite"),
                ApplicationLocations.defaultApplicationDatabasePath(
                        "Linux", home, null, null
                )
        );
    }

    private static void withProperties(
            String catalogue,
            String assets,
            String applicationDatabase,
            Runnable assertion
    ) {
        String previousCatalogue = System.getProperty(ApplicationLocations.CATALOGUE_PATH_PROPERTY);
        String previousAssets = System.getProperty(ApplicationLocations.ASSETS_ROOT_PROPERTY);
        String previousApplicationDatabase = System.getProperty(
                ApplicationLocations.APPLICATION_DATABASE_PATH_PROPERTY
        );
        try {
            setOrClear(ApplicationLocations.CATALOGUE_PATH_PROPERTY, catalogue);
            setOrClear(ApplicationLocations.ASSETS_ROOT_PROPERTY, assets);
            setOrClear(
                    ApplicationLocations.APPLICATION_DATABASE_PATH_PROPERTY,
                    applicationDatabase
            );
            assertion.run();
        } finally {
            setOrClear(ApplicationLocations.CATALOGUE_PATH_PROPERTY, previousCatalogue);
            setOrClear(ApplicationLocations.ASSETS_ROOT_PROPERTY, previousAssets);
            setOrClear(
                    ApplicationLocations.APPLICATION_DATABASE_PATH_PROPERTY,
                    previousApplicationDatabase
            );
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
