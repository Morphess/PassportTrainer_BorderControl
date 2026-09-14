package ua.passporttrainer.catalogue;

import ua.passporttrainer.database.DatabaseConnectionProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Hashes an ordered logical dump, independent of SQLite page layout. */
public final class PassportCatalogueFingerprint {

    private static final List<Query> QUERIES = List.of(
            new Query("schema_migrations",
                    "SELECT version, name, checksum_sha256 FROM schema_migrations ORDER BY version"),
            new Query("catalogue_metadata",
                    "SELECT singleton_id, manifest_schema_version, source_root, continent_count, entity_count, "
                            + "document_count, image_count, manifest_checksum_sha256 "
                            + "FROM catalogue_metadata ORDER BY singleton_id"),
            new Query("continents",
                    "SELECT id, code, display_name_uk, source_name, sort_order FROM continents ORDER BY id"),
            new Query("entities",
                    "SELECT id, canonical_key, display_name_uk, entity_type, iso_alpha2, iso_alpha3, review_status, "
                            + "notes FROM entities ORDER BY id"),
            new Query("entity_source_names",
                    "SELECT entity_id, source_name, sort_order FROM entity_source_names "
                            + "ORDER BY entity_id, sort_order"),
            new Query("entity_continents",
                    "SELECT entity_id, continent_id, is_primary, sort_order FROM entity_continents "
                            + "ORDER BY entity_id, sort_order"),
            new Query("documents",
                    "SELECT id, entity_id, document_type, title_uk, variant, review_status, quiz_eligible, "
                            + "duplicate_of_document_id, notes FROM documents ORDER BY id"),
            new Query("passport_images",
                    "SELECT id, document_id, source_relative_path, source_filename, mime_type, format, width, height, "
                            + "file_size, sha256, review_status, duplicate_of_image_id, notes "
                            + "FROM passport_images ORDER BY id"),
            new Query("aliases",
                    "SELECT kind, source_value, canonical_value, continent_id, entity_id, document_type "
                            + "FROM aliases ORDER BY kind, source_value")
    );

    private PassportCatalogueFingerprint() {
    }

    public static String sha256(DatabaseConnectionProvider connectionProvider) throws SQLException {
        Objects.requireNonNull(connectionProvider, "connectionProvider");
        try (Connection connection = connectionProvider.getConnection()) {
            return sha256(connection);
        }
    }

    public static String sha256(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        MessageDigest digest = newDigest();
        for (Query query : QUERIES) {
            update(digest, query.name());
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(query.sql())) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                int columns = metadata.getColumnCount();
                while (resultSet.next()) {
                    digest.update((byte) 1);
                    for (int column = 1; column <= columns; column++) {
                        String value = resultSet.getString(column);
                        if (value == null) {
                            digest.update((byte) 0);
                        } else {
                            digest.update((byte) 1);
                            update(digest, value);
                        }
                    }
                }
                digest.update((byte) 0);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private record Query(String name, String sql) {
    }
}
