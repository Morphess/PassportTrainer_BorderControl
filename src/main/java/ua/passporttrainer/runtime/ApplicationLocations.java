package ua.passporttrainer.runtime;

import java.nio.file.Path;
import java.util.Objects;

/** External read-only catalogue and image locations used by the desktop runtime. */
public record ApplicationLocations(Path cataloguePath, Path passportAssetsRoot) {

    public static final String CATALOGUE_PATH_PROPERTY = "passporttrainer.catalogue.path";
    public static final String ASSETS_ROOT_PROPERTY = "passporttrainer.assets.root";

    private static final Path DEFAULT_CATALOGUE_PATH =
            Path.of("data", "passport-catalogue", "passport-catalogue.sqlite");
    private static final Path DEFAULT_ASSETS_ROOT = Path.of("Зразки паспортних документів");

    public ApplicationLocations {
        cataloguePath = normalize(cataloguePath, "cataloguePath");
        passportAssetsRoot = normalize(passportAssetsRoot, "passportAssetsRoot");
    }

    public static ApplicationLocations fromSystemProperties() {
        return new ApplicationLocations(
                configuredPath(CATALOGUE_PATH_PROPERTY, DEFAULT_CATALOGUE_PATH),
                configuredPath(ASSETS_ROOT_PROPERTY, DEFAULT_ASSETS_ROOT)
        );
    }

    private static Path configuredPath(String property, Path defaultPath) {
        String value = System.getProperty(property);
        return value == null || value.isBlank() ? defaultPath : Path.of(value);
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
