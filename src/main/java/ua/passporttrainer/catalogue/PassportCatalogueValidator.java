package ua.passporttrainer.catalogue;

import ua.passporttrainer.database.SQLiteMigrationRunner;
import ua.passporttrainer.manifest.ManifestCatalogue;
import ua.passporttrainer.model.DocumentType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates canonical, review-overlay, and effective catalogue invariants before runtime use. */
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
        Metadata metadata = readMetadata(connection);
        requireCount(connection, "continents", metadata.continents());
        requireCount(connection, "entities", metadata.entities());
        requireCount(connection, "documents", metadata.documents());
        requireCount(connection, "passport_images", metadata.images());
        requireCount(connection, "review_decisions", metadata.reviewDecisionCount());
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
        requireEnumValues(connection, "entities", "manifest_review_status", REVIEW_STATUSES);
        requireEnumValues(connection, "documents", "review_status", REVIEW_STATUSES);
        requireEnumValues(connection, "documents", "manifest_review_status", REVIEW_STATUSES);
        requireEnumValues(connection, "passport_images", "review_status", REVIEW_STATUSES);
        requireEnumValues(connection, "passport_images", "manifest_review_status", REVIEW_STATUSES);
        requireNoRows(connection,
                "SELECT 1 FROM documents d JOIN passport_images i ON i.document_id = d.id "
                        + "WHERE i.manifest_review_status <> d.manifest_review_status LIMIT 1",
                "Document and image manifest review statuses differ"
        );
        requireNoRows(connection,
                "SELECT 1 FROM documents WHERE document_type = 'UNKNOWN' "
                        + "AND review_status NOT IN ('NEEDS_REVIEW', 'EXCLUDED') LIMIT 1",
                "UNKNOWN document type has an invalid effective review status"
        );
        requireNoRows(connection,
                "SELECT 1 FROM documents d JOIN entities e ON e.id = d.entity_id "
                        + "JOIN passport_images i ON i.document_id = d.id "
                        + "WHERE d.quiz_eligible = 1 AND "
                        + "(d.review_status <> 'VERIFIED' OR e.review_status <> 'VERIFIED' "
                        + "OR i.review_status <> 'VERIFIED' OR d.document_type = 'UNKNOWN' "
                        + "OR d.duplicate_of_document_id IS NOT NULL "
                        + "OR i.duplicate_of_image_id IS NOT NULL) LIMIT 1",
                "Quiz eligibility includes an unresolved or noncanonical record"
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
        validateReviewMaterialization(connection, metadata);
    }

    private static void validateReviewMaterialization(Connection connection, Metadata metadata) throws SQLException {
        try {
            ManifestCatalogue baseCatalogue = readBaseCatalogue(connection, metadata);
            ReviewDecisionSource source = readReviewSource(connection);
            if (metadata.reviewSchemaVersion() != ReviewDecisionSource.SCHEMA_VERSION) {
                throw new SQLException("Unsupported review source schema version");
            }
            if (source.records().size() != metadata.reviewDecisionCount()) {
                throw new SQLException("Review decision count differs from catalogue metadata");
            }
            if (!source.checksum().equals(metadata.reviewSourceChecksum())) {
                throw new SQLException("Review source checksum differs from materialized decisions");
            }
            ReviewDecisionValidator.ReviewMaterialization expected =
                    ReviewDecisionValidator.validate(baseCatalogue, source);
            requireEffectiveStatuses(connection, "entities", expected.entityStatuses());
            requireEffectiveStatuses(connection, "documents", expected.documentStatuses());
            requireEffectiveStatuses(connection, "passport_images", expected.imageStatuses());
            requireEffectiveEligibility(connection, expected.quizEligibility());
        } catch (ReviewValidationException | IllegalArgumentException exception) {
            throw new SQLException("Invalid materialized review overlay", exception);
        }
    }

    private static ManifestCatalogue readBaseCatalogue(Connection connection, Metadata metadata) throws SQLException {
        List<ManifestCatalogue.ContinentEntry> continents = readContinents(connection);
        Map<String, List<String>> sourceNames = readEntitySourceNames(connection);
        EntityContinents memberships = readEntityContinents(connection);
        List<ManifestCatalogue.EntityEntry> entities = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, canonical_key, display_name_uk, entity_type, manifest_review_status, notes "
                             + "FROM entities ORDER BY id"
             )) {
            while (rows.next()) {
                String id = rows.getString("id");
                entities.add(new ManifestCatalogue.EntityEntry(
                        id,
                        memberships.primary().get(id),
                        memberships.alternates().getOrDefault(id, List.of()),
                        rows.getString("canonical_key"),
                        rows.getString("display_name_uk"),
                        ManifestCatalogue.EntityType.valueOf(rows.getString("entity_type")),
                        sourceNames.getOrDefault(id, List.of()),
                        ManifestCatalogue.ReviewStatus.valueOf(rows.getString("manifest_review_status")),
                        rows.getString("notes")
                ));
            }
        }

        List<ManifestCatalogue.DocumentEntry> documents = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, entity_id, document_type, title_uk, variant, manifest_review_status, "
                             + "manifest_quiz_eligible, duplicate_of_document_id, notes FROM documents ORDER BY id"
             )) {
            while (rows.next()) {
                documents.add(new ManifestCatalogue.DocumentEntry(
                        rows.getString("id"),
                        rows.getString("entity_id"),
                        DocumentType.valueOf(rows.getString("document_type")),
                        rows.getString("title_uk"),
                        rows.getString("variant"),
                        ManifestCatalogue.ReviewStatus.valueOf(rows.getString("manifest_review_status")),
                        rows.getInt("manifest_quiz_eligible") == 1,
                        emptyIfNull(rows.getString("duplicate_of_document_id")),
                        rows.getString("notes")
                ));
            }
        }

        List<ManifestCatalogue.ImageEntry> images = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, document_id, source_relative_path, source_filename, mime_type, format, width, "
                             + "height, file_size, sha256, manifest_review_status, duplicate_of_image_id, notes "
                             + "FROM passport_images ORDER BY id"
             )) {
            while (rows.next()) {
                images.add(new ManifestCatalogue.ImageEntry(
                        rows.getString("id"),
                        rows.getString("document_id"),
                        rows.getString("source_relative_path"),
                        rows.getString("source_filename"),
                        rows.getString("mime_type"),
                        rows.getString("format"),
                        rows.getInt("width"),
                        rows.getInt("height"),
                        rows.getLong("file_size"),
                        rows.getString("sha256"),
                        ManifestCatalogue.ReviewStatus.valueOf(rows.getString("manifest_review_status")),
                        emptyIfNull(rows.getString("duplicate_of_image_id")),
                        rows.getString("notes")
                ));
            }
        }

        return new ManifestCatalogue(
                new ManifestCatalogue.Summary(
                        metadata.manifestSchemaVersion(),
                        metadata.sourceRoot(),
                        metadata.continents(),
                        metadata.entities(),
                        metadata.documents(),
                        metadata.images()
                ),
                continents,
                entities,
                documents,
                images,
                List.of()
        );
    }

    private static List<ManifestCatalogue.ContinentEntry> readContinents(Connection connection) throws SQLException {
        List<ManifestCatalogue.ContinentEntry> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, code, display_name_uk, source_name, sort_order FROM continents ORDER BY sort_order"
             )) {
            while (rows.next()) {
                result.add(new ManifestCatalogue.ContinentEntry(
                        rows.getString("id"),
                        rows.getString("code"),
                        rows.getString("display_name_uk"),
                        rows.getString("source_name"),
                        rows.getInt("sort_order")
                ));
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, List<String>> readEntitySourceNames(Connection connection) throws SQLException {
        Map<String, List<String>> mutable = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT entity_id, source_name FROM entity_source_names ORDER BY entity_id, sort_order"
             )) {
            while (rows.next()) {
                mutable.computeIfAbsent(rows.getString("entity_id"), ignored -> new ArrayList<>())
                        .add(rows.getString("source_name"));
            }
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        mutable.forEach((id, names) -> result.put(id, List.copyOf(names)));
        return Map.copyOf(result);
    }

    private static EntityContinents readEntityContinents(Connection connection) throws SQLException {
        Map<String, String> primary = new HashMap<>();
        Map<String, List<String>> mutableAlternates = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT entity_id, continent_id, is_primary FROM entity_continents "
                             + "ORDER BY entity_id, sort_order"
             )) {
            while (rows.next()) {
                String entityId = rows.getString("entity_id");
                if (rows.getInt("is_primary") == 1) {
                    primary.put(entityId, rows.getString("continent_id"));
                } else {
                    mutableAlternates.computeIfAbsent(entityId, ignored -> new ArrayList<>())
                            .add(rows.getString("continent_id"));
                }
            }
        }
        Map<String, List<String>> alternates = new LinkedHashMap<>();
        mutableAlternates.forEach((id, ids) -> alternates.put(id, List.copyOf(ids)));
        return new EntityContinents(Map.copyOf(primary), Map.copyOf(alternates));
    }

    private static ReviewDecisionSource readReviewSource(Connection connection) throws SQLException {
        List<ReviewDecisionSource.DecisionRecord> records = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT record_type, target_type, target_id, target_fingerprint_sha256, decision, reviewer, "
                             + "decided_at, note FROM review_decisions "
                             + "ORDER BY record_type, target_type, target_id"
             )) {
            while (rows.next()) {
                String timestamp = rows.getString("decided_at");
                Instant instant;
                try {
                    instant = Instant.parse(timestamp);
                } catch (DateTimeException exception) {
                    throw new SQLException("Invalid materialized review timestamp", exception);
                }
                if (!instant.toString().equals(timestamp)) {
                    throw new SQLException("Materialized review timestamp is not canonical UTC");
                }
                records.add(new ReviewDecisionSource.DecisionRecord(
                        ReviewDecisionSource.RecordType.valueOf(rows.getString("record_type")),
                        ReviewDecisionSource.TargetType.valueOf(rows.getString("target_type")),
                        rows.getString("target_id"),
                        rows.getString("target_fingerprint_sha256"),
                        ReviewDecisionSource.Decision.valueOf(rows.getString("decision")),
                        rows.getString("reviewer"),
                        instant,
                        rows.getString("note")
                ));
            }
        }
        return ReviewDecisionSource.of(records);
    }

    private static void requireEffectiveStatuses(
            Connection connection,
            String table,
            Map<String, ManifestCatalogue.ReviewStatus> expected
    ) throws SQLException {
        Map<String, ManifestCatalogue.ReviewStatus> actual = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, review_status FROM " + table)) {
            while (rows.next()) {
                actual.put(rows.getString("id"), ManifestCatalogue.ReviewStatus.valueOf(rows.getString("review_status")));
            }
        }
        if (!actual.equals(expected)) {
            throw new SQLException("Effective review statuses differ from materialized decisions for " + table);
        }
    }

    private static void requireEffectiveEligibility(
            Connection connection,
            Map<String, Boolean> expected
    ) throws SQLException {
        Map<String, Boolean> actual = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, quiz_eligible FROM documents")) {
            while (rows.next()) {
                actual.put(rows.getString("id"), rows.getInt("quiz_eligible") == 1);
            }
        }
        if (!actual.equals(expected)) {
            throw new SQLException("Effective quiz eligibility differs from explicit activation decisions");
        }
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

    private static Metadata readMetadata(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT manifest_schema_version, source_root, continent_count, entity_count, document_count, "
                             + "image_count, review_schema_version, review_decision_count, "
                             + "review_source_checksum_sha256 FROM catalogue_metadata"
             )) {
            if (!resultSet.next()) {
                throw new SQLException("Catalogue metadata is missing");
            }
            Metadata metadata = new Metadata(
                    resultSet.getInt(1),
                    resultSet.getString(2),
                    resultSet.getInt(3),
                    resultSet.getInt(4),
                    resultSet.getInt(5),
                    resultSet.getInt(6),
                    resultSet.getInt(7),
                    resultSet.getInt(8),
                    resultSet.getString(9)
            );
            if (resultSet.next()) {
                throw new SQLException("Catalogue metadata contains more than one row");
            }
            return metadata;
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

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private record Metadata(
            int manifestSchemaVersion,
            String sourceRoot,
            int continents,
            int entities,
            int documents,
            int images,
            int reviewSchemaVersion,
            int reviewDecisionCount,
            String reviewSourceChecksum
    ) {
    }

    private record EntityContinents(
            Map<String, String> primary,
            Map<String, List<String>> alternates
    ) {
    }
}
