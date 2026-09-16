package ua.passporttrainer.catalogue;

import ua.passporttrainer.database.SQLiteConnectionProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

/** Command-line entry point for deterministic catalogue rebuilds and validation. */
public final class PassportCatalogueTool {

    private PassportCatalogueTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("rebuild")) {
            Path manifestDirectory = Path.of(args[1]);
            Path reviewSource = Path.of(args[2]);
            Path databasePath = Path.of(args[3]);
            Path parent = databasePath.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            SQLiteConnectionProvider writableProvider = new SQLiteConnectionProvider(databasePath);
            new PassportCatalogueImporter(writableProvider).rebuild(manifestDirectory, reviewSource);
            String fingerprint = validateAndFingerprint(databasePath);
            System.out.println("Catalogue rebuilt; logical SHA-256=" + fingerprint);
            return;
        }
        if (args.length == 2 && args[0].equals("validate")) {
            System.out.println("Catalogue valid; logical SHA-256=" + validateAndFingerprint(Path.of(args[1])));
            return;
        }
        throw new IllegalArgumentException(
                "Usage: rebuild <manifest-directory> <review-source> <database-path> | validate <database-path>"
        );
    }

    private static String validateAndFingerprint(Path databasePath) throws Exception {
        SQLiteConnectionProvider provider = SQLiteConnectionProvider.readOnly(databasePath);
        try (Connection connection = provider.getConnection()) {
            PassportCatalogueValidator.validate(connection);
            return PassportCatalogueFingerprint.sha256(connection);
        }
    }
}
