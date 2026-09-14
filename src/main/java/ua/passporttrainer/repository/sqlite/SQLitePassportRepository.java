package ua.passporttrainer.repository.sqlite;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.repository.PassportRepository;
import ua.passporttrainer.repository.RepositoryAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reads canonical documents and image metadata from the SQLite reference catalogue. */
public final class SQLitePassportRepository implements PassportRepository {

    private static final String SELECT_DOCUMENT = "SELECT d.id, d.entity_id, d.document_type, d.title_uk, "
            + "d.variant, (SELECT i.source_relative_path FROM passport_images i "
            + "WHERE i.document_id = d.id ORDER BY i.source_relative_path LIMIT 1) AS source_relative_path "
            + "FROM documents d";

    private final DatabaseConnectionProvider connectionProvider;

    public SQLitePassportRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public Optional<PassportDocument> findById(String id) {
        Objects.requireNonNull(id, "id");
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_DOCUMENT + " WHERE d.id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapDocument(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read catalogue document by ID", exception);
        }
    }

    @Override
    public List<PassportDocument> findByCountryId(String countryId) {
        Objects.requireNonNull(countryId, "countryId");
        String sql = SELECT_DOCUMENT + " WHERE d.entity_id = ? ORDER BY d.document_type, d.title_uk, d.id";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, countryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PassportDocument> documents = new ArrayList<>();
                while (resultSet.next()) {
                    documents.add(mapDocument(resultSet));
                }
                return List.copyOf(documents);
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read catalogue documents by entity ID", exception);
        }
    }

    @Override
    public List<PassportImage> findImagesByDocumentId(String passportDocumentId) {
        Objects.requireNonNull(passportDocumentId, "passportDocumentId");
        String sql = "SELECT id, document_id, source_relative_path, source_filename, mime_type, width, height, "
                + "file_size, sha256 FROM passport_images WHERE document_id = ? ORDER BY source_relative_path, id";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passportDocumentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PassportImage> images = new ArrayList<>();
                while (resultSet.next()) {
                    images.add(mapImage(resultSet));
                }
                return List.copyOf(images);
            }
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read catalogue images by document ID", exception);
        }
    }

    private static PassportDocument mapDocument(ResultSet resultSet) throws SQLException {
        return new PassportDocument(
                resultSet.getString("id"),
                resultSet.getString("entity_id"),
                DocumentType.valueOf(resultSet.getString("document_type")),
                resultSet.getString("title_uk"),
                resultSet.getString("variant"),
                resultSet.getString("source_relative_path")
        );
    }

    private static PassportImage mapImage(ResultSet resultSet) throws SQLException {
        return new PassportImage(
                resultSet.getString("id"),
                resultSet.getString("document_id"),
                resultSet.getString("source_relative_path"),
                resultSet.getString("source_filename"),
                resultSet.getString("mime_type"),
                resultSet.getInt("width"),
                resultSet.getInt("height"),
                resultSet.getLong("file_size"),
                resultSet.getString("sha256")
        );
    }
}
