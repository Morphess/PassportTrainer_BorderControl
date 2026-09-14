package ua.passporttrainer.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteMigrationRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsVersionOneSchemaExactlyOnce() throws Exception {
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
                    "entity_source_names", "entity_continents", "documents", "passport_images", "aliases"
            )));
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT version FROM schema_migrations ORDER BY version"
            )) {
                assertTrue(resultSet.next());
                assertEquals(SQLiteMigrationRunner.CURRENT_VERSION, resultSet.getInt(1));
                assertTrue(!resultSet.next());
            }
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
}
