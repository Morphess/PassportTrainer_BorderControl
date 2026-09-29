package ua.passporttrainer.repository.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.model.MediaMetadata;
import ua.passporttrainer.model.MediaReferenceType;
import ua.passporttrainer.repository.MediaRepository;
import ua.passporttrainer.repository.RepositoryAccessException;

/** Persists media references and metadata without touching media files. */
public final class SQLiteMediaRepository implements MediaRepository {

    private static final String COLUMNS = "media_id, reference_type, reference_value, "
            + "mime_type, size_bytes, sha256, created_by, created_at, updated_at, archived";
    private static final String INSERT = "INSERT INTO media_metadata (" + COLUMNS
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING " + COLUMNS;
    private static final String UPDATE = "UPDATE media_metadata SET reference_type = ?, "
            + "reference_value = ?, mime_type = ?, size_bytes = ?, sha256 = ?, "
            + "updated_at = ?, archived = ? WHERE media_id = ? RETURNING " + COLUMNS;
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS
            + " FROM media_metadata WHERE media_id = ?";
    private static final String SELECT_ALL = "SELECT " + COLUMNS
            + " FROM media_metadata ORDER BY created_at, media_id";

    private final DatabaseConnectionProvider connectionProvider;

    public SQLiteMediaRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public MediaMetadata create(MediaMetadata media) {
        Objects.requireNonNull(media, "media");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, media.mediaId());
            statement.setString(2, media.referenceType().name());
            statement.setString(3, media.reference());
            statement.setString(4, media.mimeType());
            setOptionalLong(statement, 5, media.sizeBytes());
            setOptionalText(statement, 6, media.sha256());
            statement.setLong(7, media.createdBy());
            statement.setString(8, media.createdAt().toString());
            statement.setString(9, media.updatedAt().toString());
            statement.setInt(10, media.archived() ? 1 : 0);
            return requireReturnedMedia(statement, "Media metadata insert returned no row");
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot create media metadata", exception);
        }
    }

    @Override
    public MediaMetadata update(MediaMetadata media) {
        Objects.requireNonNull(media, "media");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, media.referenceType().name());
            statement.setString(2, media.reference());
            statement.setString(3, media.mimeType());
            setOptionalLong(statement, 4, media.sizeBytes());
            setOptionalText(statement, 5, media.sha256());
            statement.setString(6, media.updatedAt().toString());
            statement.setInt(7, media.archived() ? 1 : 0);
            statement.setString(8, media.mediaId());
            return requireReturnedMedia(
                    statement, "Unknown media metadata ID: " + media.mediaId()
            );
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot update media metadata", exception);
        }
    }

    @Override
    public Optional<MediaMetadata> findById(String mediaId) {
        requireId(mediaId, "mediaId");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setString(1, mediaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read media metadata by ID", exception);
        }
    }

    @Override
    public List<MediaMetadata> findAll() {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            List<MediaMetadata> media = new ArrayList<>();
            while (resultSet.next()) {
                media.add(map(resultSet));
            }
            return List.copyOf(media);
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot list media metadata", exception);
        }
    }

    private static MediaMetadata requireReturnedMedia(
            PreparedStatement statement,
            String missingMessage
    ) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException(missingMessage);
            }
            return map(resultSet);
        }
    }

    private static MediaMetadata map(ResultSet resultSet) throws SQLException {
        long sizeBytes = resultSet.getLong("size_bytes");
        OptionalLong optionalSize = resultSet.wasNull()
                ? OptionalLong.empty()
                : OptionalLong.of(sizeBytes);
        try {
            return new MediaMetadata(
                    resultSet.getString("media_id"),
                    MediaReferenceType.valueOf(resultSet.getString("reference_type")),
                    resultSet.getString("reference_value"),
                    resultSet.getString("mime_type"),
                    optionalSize,
                    Optional.ofNullable(resultSet.getString("sha256")),
                    resultSet.getLong("created_by"),
                    Instant.parse(resultSet.getString("created_at")),
                    Instant.parse(resultSet.getString("updated_at")),
                    resultSet.getInt("archived") == 1
            );
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new SQLException("Invalid persisted media metadata", exception);
        }
    }

    private static void setOptionalLong(
            PreparedStatement statement,
            int index,
            OptionalLong value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setLong(index, value.getAsLong());
        } else {
            statement.setNull(index, Types.BIGINT);
        }
    }

    private static void setOptionalText(
            PreparedStatement statement,
            int index,
            Optional<String> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.get());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static void requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
