package ua.passporttrainer.catalogue;

import ua.passporttrainer.database.SQLiteMigrationRunner;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/** Validates the relational catalogue after import and before runtime use. */
public final class PassportCatalogueValidator {

    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "ORDINARY_PASSPORT", "DIPLOMATIC_PASSPORT", "SERVICE_PASSPORT", "NATIONAL_ID", "UNKNOWN"
    );
    private static final Set<String> REVIEW_STATUSES = Set.of(
            "VERIFIED", "AUTO_CLASSIFIED", "NEEDS_REVIEW", "EXCLUDED"
    );

    private PassportCatalogueValidator() {
    }

    public static void validate(Connection connection) throws SQLException {
        requireForeignKeys(connection);
        requireCurrentMigration(connection);
        Counts expected = readExpectedCounts(connection);
        requireCount(connection, "continents", expected.continents());
        requireCount(connection, "entities", expected.entities());
        requireCount(connection, "documents", expected.documents());
        requireCount(connection, "passport_images", expected.images());
        requireNoForeignKeyViolations(connection);
        requireNoRows(connection,
                "SELECT 1 FROM entities e WHERE "
                        + "(SELECT COUNT(*) FROM entity_continents ec "
                        + " WHERE ec.entity_id = e.id AND ec.is_primary = 1) <> 1 LIMIT 1",
                "Every entity must have exactly one primary continent"
        );
        requireNoRows(connection,
                "SELECT 1 FROM entities e WHERE "
                        + "(SELECT COUNT(*) FROM entity_source_names n WHERE n.entity_id = e.id) = 0 LIMIT 1",
                "Every entity must retain at least one source name"
        );
        requireNoRows(connection,
                "SELECT 1 FROM documents d WHERE "
                        + "(SELECT COUNT(*) FROM passport_images i WHERE i.document_id = d.id) <> 1 LIMIT 1",
                "Every Stage 5 document must have exactly one image"
        );
        requireEnumValues(connection, "documents", "document_type", DOCUMENT_TYPES);
        requireEnumValues(connection, "entities", "review_status", REVIEW_STATUSES);
        requireEnumValues(connection, "documents", "review_status", REVIEW_STATUSES);
        requireEnumValues(connection, "passport_images", "review_status", REVIEW_STATUSES);
        requireNoRows(connection,
                "SELECT 1 FROM documents d JOIN passport_images i ON i.document_id = d.id "
                        + "WHERE i.review_status <> d.review_status LIMIT 1",
                "Document and image review statuses differ"
        );
        requireNoRows(connection,
                "SELECT 1 FROM documents WHERE document_type = 'UNKNOWN' "
                        + "AND review_status NOT IN ('NEEDS_REVIEW', 'EXCLUDED') LIMIT 1",
                "UNKNOWN document type does not require review"
        );
        requireNoRows(connection,
                "SELECT 1 FROM documents d JOIN entities e ON e.id = d.entity_id "
                        + "WHERE d.quiz_eligible = 1 AND "
                        + "(d.review_status <> 'VERIFIED' OR e.review_status <> 'VERIFIED' "
                        + "OR d.document_type = 'UNKNOWN') LIMIT 1",
                "Quiz eligibility includes an unresolved record"
        );
        requireNoRows(connection,
                "SELECT 1 FROM documents d JOIN documents target ON target.id = d.duplicate_of_document_id "
                        + "WHERE target.duplicate_of_document_id IS NOT NULL LIMIT 1",
                "Document duplicate chains are forbidden"
        );
        requireNoRows(connection,
                "SELECT 1 FROM passport_images i "
                        + "JOIN passport_images target ON target.id = i.duplicate_of_image_id "
                        + "WHERE target.duplicate_of_image_id IS NOT NULL LIMIT 1",
                "Image duplicate chains are forbidden"
        );
        requireNoRows(connection,
                "SELECT 1 FROM passport_images i "
                        + "JOIN passport_images target ON target.id = i.duplicate_of_image_id "
                        + "WHERE i.sha256 <> target.sha256 LIMIT 1",
                "Duplicate images have different SHA-256 values"
        );
        requireNoRows(connection,
                "SELECT 1 FROM documents d JOIN passport_images i ON i.document_id = d.id "
                        + "WHERE (d.duplicate_of_document_id IS NULL) <> (i.duplicate_of_image_id IS NULL) "
                        + "OR (d.duplicate_of_document_id IS NOT NULL AND NOT EXISTS ("
                        + "SELECT 1 FROM passport_images target "
                        + "WHERE target.document_id = d.duplicate_of_document_id "
                        + "AND target.id = i.duplicate_of_image_id)) LIMIT 1",
                "Document and image duplicate relationships differ"
        );
        requireNoRows(connection,
                "SELECT 1 FROM passport_images WHERE source_relative_path = '' "
                        + "OR substr(source_relative_path, 1, 1) = '/' "
                        + "OR instr(source_relative_path, '\\') > 0 "
                        + "OR source_relative_path LIKE '../%' "
                        + "OR source_relative_path LIKE '%/../%' LIMIT 1",
                "Invalid source-relative path"
        );
        requireNoRows(connection,
                "SELECT 1 FROM passport_images WHERE "
                        + "length(source_relative_path) <= length(source_filename) "
                        + "OR substr(source_relative_path, -length(source_filename)) <> source_filename "
                        + "OR substr(source_relative_path, -length(source_filename) - 1, 1) <> '/' LIMIT 1",
                "Source filename does not match source-relative path"
        );
        requireNoRows(connection,
                "SELECT source_relative_path FROM passport_images "
                        + "GROUP BY source_relative_path HAVING COUNT(*) > 1 LIMIT 1",
                "Duplicate source-relative path"
        );
        requireNoRows(connection,
                "SELECT 1 FROM aliases a LEFT JOIN continents c ON c.id = a.continent_id "
                        + "LEFT JOIN entities e ON e.id = a.entity_id "
                        + "WHERE (a.kind = 'CONTINENT' AND a.canonical_value <> c.code) "
                        + "OR (a.kind = 'ENTITY' AND a.canonical_value <> e.canonical_key) "
                        + "OR (a.kind = 'DOCUMENT_TYPE' AND a.canonical_value <> a.document_type) LIMIT 1",
                "Alias canonical value does not match its target"
        );
    }

    private static void requireForeignKeys(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
            if (!resultSet.next() || resultSet.getInt(1) != 1) {
                throw new SQLException("SQLite foreign keys are not enabled for this connection");
            }
        }
    }

    private static void requireCurrentMigration(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT MAX(version) FROM schema_migrations")) {
            if (!resultSet.next() || resultSet.getInt(1) != SQLiteMigrationRunner.CURRENT_VERSION) {
                throw new SQLException("Unsupported catalogue schema version");
            }
        }
    }

    private static Counts readExpectedCounts(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT continent_count, entity_count, document_count, image_count FROM catalogue_metadata"
             )) {
            if (!resultSet.next()) {
                throw new SQLException("Catalogue metadata is missing");
            }
            Counts counts = new Counts(
                    resultSet.getInt(1), resultSet.getInt(2), resultSet.getInt(3), resultSet.getInt(4)
            );
            if (resultSet.next()) {
                throw new SQLException("Catalogue metadata contains more than one row");
            }
            return counts;
        }
    }

    private static void requireCount(Connection connection, String table, int expected) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (!resultSet.next() || resultSet.getInt(1) != expected) {
                throw new SQLException("Catalogue row count differs for " + table + "; expected " + expected);
            }
        }
    }

    private static void requireNoForeignKeyViolations(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (resultSet.next()) {
                throw new SQLException(
                        "Foreign key violation in table " + resultSet.getString("table")
                                + " at row " + resultSet.getString("rowid")
                );
            }
        }
    }

    private static void requireEnumValues(
            Connection connection,
            String table,
            String column,
            Set<String> accepted
    ) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DISTINCT " + column + " FROM " + table)) {
            while (resultSet.next()) {
                String value = resultSet.getString(1);
                if (!accepted.contains(value)) {
                    throw new SQLException("Invalid " + table + "." + column + " value: " + value);
                }
            }
        }
    }

    private static void requireNoRows(Connection connection, String sql, String message) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                throw new SQLException(message);
            }
        }
    }

    private record Counts(int continents, int entities, int documents, int images) {
    }
}
