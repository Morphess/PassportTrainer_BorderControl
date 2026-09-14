package ua.passporttrainer.manifest;

import java.nio.file.Path;

/** Command-line entry point for deterministic manifest generation and validation. */
public final class PassportManifestTool {
    private PassportManifestTool() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 4 && arguments[0].equals("generate")) {
            ManifestCatalogue catalogue = new PassportManifestGenerator().generate(
                    Path.of(arguments[1]), Path.of(arguments[2]), Path.of(arguments[3])
            );
            System.out.printf(
                    "Generated schema %d: %d continents, %d entities, %d documents, %d images%n",
                    catalogue.summary().schemaVersion(), catalogue.continents().size(), catalogue.entities().size(),
                    catalogue.documents().size(), catalogue.images().size()
            );
            return;
        }
        if (arguments.length == 3 && arguments[0].equals("validate")) {
            PassportManifestValidator.validate(Path.of(arguments[1]), Path.of(arguments[2]));
            System.out.println("Manifest is valid");
            return;
        }
        throw new IllegalArgumentException(
                "Usage: generate <source-root> <metadata-directory> <output-directory> "
                        + "or validate <source-root> <manifest-directory>"
        );
    }
}
