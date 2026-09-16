package ua.passporttrainer.catalogue;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.database.SQLiteMigrationRunner;
import ua.passporttrainer.manifest.ManifestCatalogue;
import ua.passporttrainer.manifest.PassportManifestValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Rebuilds an effective SQLite reference catalogue from Stage 5 data and explicit review decisions. */
public final class PassportCatalogueImporter {

    private static final List<String> MANIFEST_FILES = List.of(
            "manifest.tsv", "continents.tsv", "entities.tsv", "documents.tsv", "images.tsv", "aliases.tsv"
    );

    private final DatabaseConnectionProvider connectionProvider;
    private final SQLiteMigrationRunner migrationRunner;

    public PassportCatalogueImporter(DatabaseConnectionProvider connectionProvider) {
        this(connectionProvider, new SQLiteMigrationRunner());
    }

    PassportCatalogueImporter(
            DatabaseConnectionProvider connectionProvider,
            SQLiteMigrationRunner migrationRunner
    ) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.migrationRunner = Objects.requireNonNull(migrationRunner, "migrationRunner");
    }

    public void rebuild(Path manifestDirectory, Path reviewSourcePath) throws IOException, SQLException {
        Objects.requireNonNull(manifestDirectory, "manifestDirectory");
        Objects.requireNonNull(reviewSourcePath, "reviewSourcePath");
        ManifestCatalogue catalogue = ManifestCatalogue.read(manifestDirectory);
        PassportManifestValidator.validate(catalogue);
        ReviewDecisionSource reviewSource = ReviewDecisionSource.read(reviewSourcePath);
        ReviewDecisionValidator.ReviewMaterialization materialization =
                ReviewDecisionValidator.validate(catalogue, reviewSource);
        String manifestChecksum = manifestChecksum(manifestDirectory);

        migrationRunner.migrate(connectionProvider);
        try (Connection connection = connectionProvider.getConnection()) {
            requireForeignKeys(connection);
            connection.setAutoCommit(false);
            try {
                clearCatalogue(connection);
                insertContinents(connection, catalogue);
                insertEntities(connection, catalogue, materialization);
                insertEntitySourceNames(connection, catalogue);
                insertEntityContinents(connection, catalogue);
                insertDocuments(connection, catalogue, materialization);
                insertImages(connection, catalogue, materialization);
                updateDocumentDuplicates(connection, catalogue);
                updateImageDuplicates(connection, catalogue);
                insertAliases(connection, catalogue);
                insertReviewDecisions(connection, reviewSource);
                insertMetadata(connection, catalogue, manifestChecksum, reviewSource);
                PassportCatalogueValidator.validate(connection);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private static void clearCatalogue(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM review_decisions");
            statement.executeUpdate("DELETE FROM aliases");
            statement.executeUpdate("DELETE FROM passport_images");
            statement.executeUpdate("DELETE FROM documents");
            statement.executeUpdate("DELETE FROM entity_continents");
            statement.executeUpdate("DELETE FROM entity_source_names");
            statement.executeUpdate("DELETE FROM entities");
            statement.executeUpdate("DELETE FROM continents");
            statement.executeUpdate("DELETE FROM catalogue_metadata");
        }
    }

    private static void insertContinents(Connection connection, ManifestCatalogue catalogue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO continents(id, code, display_name_uk, source_name, sort_order) VALUES (?, ?, ?, ?, ?)"
        )) {
            for (ManifestCatalogue.ContinentEntry continent : catalogue.continents()) {
                statement.setString(1, continent.id());
                statement.setString(2, continent.code());
                statement.setString(3, continent.displayNameUk());
                statement.setString(4, continent.sourceName());
                statement.setInt(5, continent.sortOrder());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertEntities(
            Connection connection,
            ManifestCatalogue catalogue,
            ReviewDecisionValidator.ReviewMaterialization materialization
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO entities(id, canonical_key, display_name_uk, entity_type, iso_alpha2, iso_alpha3, "
                        + "review_status, notes, manifest_review_status) "
                        + "VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, ?)"
        )) {
            for (ManifestCatalogue.EntityEntry entity : catalogue.entities()) {
                statement.setString(1, entity.id());
                statement.setString(2, entity.canonicalKey());
                statement.setString(3, entity.displayNameUk());
                statement.setString(4, entity.entityType().name());
                statement.setString(5, materialization.entityStatuses().get(entity.id()).name());
                statement.setString(6, entity.notes());
                statement.setString(7, entity.reviewStatus().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertEntitySourceNames(Connection connection, ManifestCatalogue catalogue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO entity_source_names(entity_id, source_name, sort_order) VALUES (?, ?, ?)"
        )) {
            for (ManifestCatalogue.EntityEntry entity : catalogue.entities()) {
                for (int index = 0; index < entity.sourceNames().size(); index++) {
                    statement.setString(1, entity.id());
                    statement.setString(2, entity.sourceNames().get(index));
                    statement.setInt(3, index);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static void insertEntityContinents(Connection connection, ManifestCatalogue catalogue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO entity_continents(entity_id, continent_id, is_primary, sort_order) VALUES (?, ?, ?, ?)"
        )) {
            for (ManifestCatalogue.EntityEntry entity : catalogue.entities()) {
                addEntityContinent(statement, entity.id(), entity.primaryContinentId(), true, 0);
                for (int index = 0; index < entity.alternateContinentIds().size(); index++) {
                    addEntityContinent(
                            statement, entity.id(), entity.alternateContinentIds().get(index), false, index + 1
                    );
                }
            }
            statement.executeBatch();
        }
    }

    private static void addEntityContinent(
            PreparedStatement statement,
            String entityId,
            String continentId,
            boolean primary,
            int sortOrder
    ) throws SQLException {
        statement.setString(1, entityId);
        statement.setString(2, continentId);
        statement.setInt(3, primary ? 1 : 0);
        statement.setInt(4, sortOrder);
        statement.addBatch();
    }

    private static void insertDocuments(
            Connection connection,
            ManifestCatalogue catalogue,
            ReviewDecisionValidator.ReviewMaterialization materialization
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO documents(id, entity_id, document_type, title_uk, variant, review_status, "
                        + "quiz_eligible, duplicate_of_document_id, notes, manifest_review_status, "
                        + "manifest_quiz_eligible) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)"
        )) {
            for (ManifestCatalogue.DocumentEntry document : catalogue.documents()) {
                statement.setString(1, document.id());
                statement.setString(2, document.entityId());
                statement.setString(3, document.documentType().name());
                statement.setString(4, document.titleUk());
                statement.setString(5, document.variant());
                statement.setString(6, materialization.documentStatuses().get(document.id()).name());
                statement.setInt(7, materialization.quizEligibility().get(document.id()) ? 1 : 0);
                statement.setString(8, document.notes());
                statement.setString(9, document.reviewStatus().name());
                statement.setInt(10, document.quizEligible() ? 1 : 0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertImages(
            Connection connection,
            ManifestCatalogue catalogue,
            ReviewDecisionValidator.ReviewMaterialization materialization
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO passport_images(id, document_id, source_relative_path, source_filename, mime_type, "
                        + "format, width, height, file_size, sha256, review_status, duplicate_of_image_id, notes, "
                        + "manifest_review_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)"
        )) {
            for (ManifestCatalogue.ImageEntry image : catalogue.images()) {
                statement.setString(1, image.id());
                statement.setString(2, image.documentId());
                statement.setString(3, image.sourcePath());
                statement.setString(4, image.sourceFilename());
                statement.setString(5, image.mimeType());
                statement.setString(6, image.format());
                statement.setInt(7, image.width());
                statement.setInt(8, image.height());
                statement.setLong(9, image.fileSize());
                statement.setString(10, image.sha256());
                statement.setString(11, materialization.imageStatuses().get(image.id()).name());
                statement.setString(12, image.notes());
                statement.setString(13, image.reviewStatus().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void updateDocumentDuplicates(Connection connection, ManifestCatalogue catalogue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE documents SET duplicate_of_document_id = ? WHERE id = ?"
        )) {
            for (ManifestCatalogue.DocumentEntry document : catalogue.documents()) {
                if (!document.duplicateOfDocumentId().isEmpty()) {
                    statement.setString(1, document.duplicateOfDocumentId());
                    statement.setString(2, document.id());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static void updateImageDuplicates(Connection connection, ManifestCatalogue catalogue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE passport_images SET duplicate_of_image_id = ? WHERE id = ?"
        )) {
            for (ManifestCatalogue.ImageEntry image : catalogue.images()) {
                if (!image.duplicateOfImageId().isEmpty()) {
                    statement.setString(1, image.duplicateOfImageId());
                    statement.setString(2, image.id());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static void insertAliases(Connection connection, ManifestCatalogue catalogue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO aliases(kind, source_value, canonical_value, continent_id, entity_id, document_type) "
                        + "VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            for (ManifestCatalogue.AliasEntry alias : catalogue.aliases()) {
                statement.setString(1, alias.kind().name());
                statement.setString(2, alias.sourceValue());
                statement.setString(3, alias.canonicalValue());
                statement.setNull(4, Types.VARCHAR);
                statement.setNull(5, Types.VARCHAR);
                statement.setNull(6, Types.VARCHAR);
                switch (alias.kind()) {
                    case CONTINENT -> statement.setString(4, alias.targetId());
                    case ENTITY -> statement.setString(5, alias.targetId());
                    case DOCUMENT_TYPE -> statement.setString(6, alias.canonicalValue());
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertReviewDecisions(
            Connection connection,
            ReviewDecisionSource source
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO review_decisions(record_type, target_type, target_id, "
                        + "target_fingerprint_sha256, decision, reviewer, decided_at, note) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (ReviewDecisionSource.DecisionRecord record : source.records()) {
                statement.setString(1, record.recordType().name());
                statement.setString(2, record.targetType().name());
                statement.setString(3, record.targetId());
                statement.setString(4, record.targetFingerprintSha256());
                statement.setString(5, record.decision().name());
                statement.setString(6, record.reviewer());
                statement.setString(7, record.decidedAt().toString());
                statement.setString(8, record.note());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertMetadata(
            Connection connection,
            ManifestCatalogue catalogue,
            String manifestChecksum,
            ReviewDecisionSource reviewSource
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO catalogue_metadata(singleton_id, manifest_schema_version, source_root, continent_count, "
                        + "entity_count, document_count, image_count, manifest_checksum_sha256, "
                        + "review_schema_version, review_decision_count, review_source_checksum_sha256) "
                        + "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ManifestCatalogue.Summary summary = catalogue.summary();
            statement.setInt(1, summary.schemaVersion());
            statement.setString(2, summary.sourceRoot());
            statement.setInt(3, summary.continentCount());
            statement.setInt(4, summary.entityCount());
            statement.setInt(5, summary.documentCount());
            statement.setInt(6, summary.imageCount());
            statement.setString(7, manifestChecksum);
            statement.setInt(8, ReviewDecisionSource.SCHEMA_VERSION);
            statement.setInt(9, reviewSource.records().size());
            statement.setString(10, reviewSource.checksum());
            statement.executeUpdate();
        }
    }

    static String manifestChecksum(Path manifestDirectory) throws IOException {
        MessageDigest digest = sha256Digest();
        for (String fileName : MANIFEST_FILES) {
            digest.update(fileName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            Path path = manifestDirectory.resolve(fileName);
            try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static void requireForeignKeys(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
            if (!resultSet.next() || resultSet.getInt(1) != 1) {
                throw new SQLException("SQLite foreign keys are not enabled for import");
            }
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
