package ua.passporttrainer;

import ua.passporttrainer.catalogue.PassportCatalogueImporter;
import ua.passporttrainer.database.SQLiteConnectionProvider;

import java.nio.file.Path;

public final class CatalogueTestSupport {

    public static final Path MANIFEST_DIRECTORY = Path.of("data", "passport-manifest");
    public static final Path REVIEW_SOURCE =
            Path.of("data", "passport-review", "review-decisions.tsv");

    private CatalogueTestSupport() {
    }

    public static SQLiteConnectionProvider importCatalogue(Path databasePath) throws Exception {
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(databasePath);
        new PassportCatalogueImporter(provider).rebuild(MANIFEST_DIRECTORY, REVIEW_SOURCE);
        return provider;
    }
}
