package ua.passporttrainer.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.passporttrainer.catalogue.PassportCatalogueValidator;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteMigrationRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsCurrentSchemaExactlyOnce() throws Exception {
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(
                temporaryDirectory.resolve("schema.sqlite")
        );
        SQLiteMigrationRunner runner = new SQLiteMigrationRunner();

        runner.migrate(provider);
        runner.migrate(provider);

        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            Set<String> tables = new HashSet<>();
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT name FROM sqlite_schema WHERE type = 'table' ORDER BY name"
            )) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString(1));
                }
            }
            assertTrue(tables.containsAll(Set.of(
                    "schema_migrations", "catalogue_metadata", "continents", "entities",
                    "entity_source_names", "entity_continents", "documents", "passport_images", "aliases",
                    "review_decisions"
            )));
            List<Integer> versions = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT version FROM schema_migrations ORDER BY version"
            )) {
                while (resultSet.next()) {
                    versions.add(resultSet.getInt(1));
                }
            }
            assertEquals(List.of(1, SQLiteMigrationRunner.CURRENT_VERSION), versions);
        }
    }

    @Test
    void versionOneUpgradeCopiesProvenanceButCreatesNoImplicitApprovals() throws Exception {
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(
                temporaryDirectory.resolve("version-one.sqlite")
        );
        createVersionOneCatalogue(provider);

        new SQLiteMigrationRunner().migrate(provider);

        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals("VERIFIED", scalarText(statement,
                    "SELECT manifest_review_status FROM entities WHERE id = 'entity:test'"));
            assertEquals("NEEDS_REVIEW", scalarText(statement,
                    "SELECT review_status FROM entities WHERE id = 'entity:test'"));
            assertEquals("VERIFIED", scalarText(statement,
                    "SELECT manifest_review_status FROM documents WHERE id = 'document:test'"));
            assertEquals("NEEDS_REVIEW", scalarText(statement,
                    "SELECT review_status FROM documents WHERE id = 'document:test'"));
            assertEquals(1, scalarInt(statement,
                    "SELECT manifest_quiz_eligible FROM documents WHERE id = 'document:test'"));
            assertEquals(0, scalarInt(statement,
                    "SELECT quiz_eligible FROM documents WHERE id = 'document:test'"));
            assertEquals("VERIFIED", scalarText(statement,
                    "SELECT manifest_review_status FROM passport_images WHERE id = 'image:test'"));
            assertEquals("NEEDS_REVIEW", scalarText(statement,
                    "SELECT review_status FROM passport_images WHERE id = 'image:test'"));
            assertEquals(0, scalarInt(statement, "SELECT COUNT(*) FROM review_decisions"));
            assertThrows(java.sql.SQLException.class, () -> PassportCatalogueValidator.validate(connection));
        }
    }

    @Test
    void everyProvidedConnectionEnablesForeignKeys() throws Exception {
        SQLiteConnectionProvider provider = new SQLiteConnectionProvider(
                temporaryDirectory.resolve("foreign-keys.sqlite")
        );
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    private static void createVersionOneCatalogue(SQLiteConnectionProvider provider) throws Exception {
        byte[] migration;
        try (InputStream input = SQLiteMigrationRunnerTest.class.getResourceAsStream(
                "/db/migration/V001__create_catalogue.sql"
        )) {
            if (input == null) {
                throw new IllegalStateException("Missing V001 migration");
            }
            migration = input.readAllBytes();
        }
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(migration));
        try (Connection connection = provider.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : new String(migration, StandardCharsets.UTF_8).split(";", -1)) {
                if (!sql.isBlank()) {
                    statement.execute(sql.strip());
                }
            }
            statement.executeUpdate("INSERT INTO schema_migrations VALUES "
                    + "(1, 'create_catalogue', '" + checksum + "')");
            statement.executeUpdate("INSERT INTO catalogue_metadata VALUES "
                    + "(1, 1, 'source', 1, 1, 1, 1, '" + "0".repeat(64) + "')");
            statement.executeUpdate("INSERT INTO continents VALUES "
                    + "('continent:test', 'TEST', 'Test', 'Test', 1)");
            statement.executeUpdate("INSERT INTO entities VALUES "
                    + "('entity:test', 'test', 'Test', 'COUNTRY', NULL, NULL, 'VERIFIED', '')");
            statement.executeUpdate("INSERT INTO entity_source_names VALUES ('entity:test', 'Test', 0)");
            statement.executeUpdate("INSERT INTO entity_continents VALUES "
                    + "('entity:test', 'continent:test', 1, 0)");
            statement.executeUpdate("INSERT INTO documents VALUES "
                    + "('document:test', 'entity:test', 'ORDINARY_PASSPORT', 'Passport', '', "
                    + "'VERIFIED', 1, NULL, '')");
            statement.executeUpdate("INSERT INTO passport_images VALUES "
                    + "('image:test', 'document:test', 'Test/passport.png', 'passport.png', "
                    + "'image/png', 'PNG', 10, 10, 100, '" + "1".repeat(64) + "', "
                    + "'VERIFIED', NULL, '')");
        }
    }

    private static int scalarInt(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static String scalarText(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }
}
