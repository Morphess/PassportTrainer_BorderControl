package ua.passporttrainer.runtime;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** External read-only content and writable per-user application-data locations. */
public record ApplicationLocations(
        Path cataloguePath,
        Path passportAssetsRoot,
        Path applicationDatabasePath
) {

    public static final String CATALOGUE_PATH_PROPERTY = "passporttrainer.catalogue.path";
    public static final String ASSETS_ROOT_PROPERTY = "passporttrainer.assets.root";
    public static final String APPLICATION_DATABASE_PATH_PROPERTY =
            "passporttrainer.application.database.path";

    private static final Path DEFAULT_CATALOGUE_PATH =
            Path.of("data", "passport-catalogue", "passport-catalogue.sqlite");
    private static final Path DEFAULT_ASSETS_ROOT = Path.of("Зразки паспортних документів");

    public ApplicationLocations {
        cataloguePath = normalize(cataloguePath, "cataloguePath");
        passportAssetsRoot = normalize(passportAssetsRoot, "passportAssetsRoot");
        applicationDatabasePath = normalize(
                applicationDatabasePath, "applicationDatabasePath"
        );
    }

    public static ApplicationLocations fromSystemProperties() {
        return new ApplicationLocations(
                configuredPath(CATALOGUE_PATH_PROPERTY, DEFAULT_CATALOGUE_PATH),
                configuredPath(ASSETS_ROOT_PROPERTY, DEFAULT_ASSETS_ROOT),
                configuredPath(
                        APPLICATION_DATABASE_PATH_PROPERTY,
                        defaultApplicationDatabasePath(
                                System.getProperty("os.name", ""),
                                Path.of(System.getProperty("user.home", ".")),
                                System.getenv("LOCALAPPDATA"),
                                System.getenv("XDG_DATA_HOME")
                        )
                )
        );
    }

    static Path defaultApplicationDatabasePath(
            String operatingSystem,
            Path userHome,
            String localAppData,
            String xdgDataHome
    ) {
        Objects.requireNonNull(operatingSystem, "operatingSystem");
        Objects.requireNonNull(userHome, "userHome");
        String normalizedOperatingSystem = operatingSystem.toLowerCase(Locale.ROOT);
        if (normalizedOperatingSystem.startsWith("windows")) {
            Path root = pathOrDefault(
                    localAppData,
                    userHome.resolve(Path.of("AppData", "Local"))
            );
            return root.resolve(Path.of("PassportTrainer", "passport-trainer.sqlite"));
        }
        if (normalizedOperatingSystem.startsWith("mac")) {
            return userHome.resolve(
                    Path.of("Library", "Application Support", "PassportTrainer",
                            "passport-trainer.sqlite")
            );
        }
        Path root = pathOrDefault(
                xdgDataHome,
                userHome.resolve(Path.of(".local", "share"))
        );
        return root.resolve(Path.of("passport-trainer", "passport-trainer.sqlite"));
    }

    private static Path configuredPath(String property, Path defaultPath) {
        String value = System.getProperty(property);
        return value == null || value.isBlank() ? defaultPath : Path.of(value);
    }

    private static Path pathOrDefault(String configuredPath, Path defaultPath) {
        return configuredPath == null || configuredPath.isBlank()
                ? defaultPath
                : Path.of(configuredPath);
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
