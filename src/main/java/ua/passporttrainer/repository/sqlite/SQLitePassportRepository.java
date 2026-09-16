package ua.passporttrainer.repository.sqlite;

import ua.passporttrainer.database.DatabaseConnectionProvider;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.model.ReviewStatus;
import ua.passporttrainer.model.QuizCandidate;
import ua.passporttrainer.repository.PassportRepository;
import ua.passporttrainer.repository.RepositoryAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reads canonical documents and image metadata from the SQLite reference catalogue. */
public final class SQLitePassportRepository implements PassportRepository {

    private static final String SELECT_DOCUMENT = "SELECT d.id, d.entity_id, d.document_type, d.title_uk, "
            + "d.variant, d.review_status, "
            + "(SELECT i.source_relative_path FROM passport_images i "
            + "WHERE i.document_id = d.id ORDER BY i.source_relative_path LIMIT 1) AS source_relative_path, "
            + "(SELECT COUNT(*) FROM passport_images i WHERE i.document_id = d.id) AS image_count "
            + "FROM documents d";

    private static final String SELECT_QUIZ_CANDIDATES = "SELECT d.id AS document_id, d.entity_id, "
            + "e.display_name_uk AS entity_display_name, e.review_status AS entity_review_status, "
            + "d.document_type, d.title_uk AS document_title, "
            + "d.review_status AS document_review_status, d.quiz_eligible, "
            + "COALESCE(d.duplicate_of_document_id, d.id) AS canonical_document_id, "
            + "i.id AS image_id, i.source_relative_path AS image_relative_path, i.sha256 AS image_sha256, "
            + "i.review_status AS image_review_status, "
            + "COALESCE(i.duplicate_of_image_id, i.id) AS canonical_image_id, "
            + "ec.continent_id FROM documents d "
            + "JOIN entities e ON e.id = d.entity_id "
            + "JOIN passport_images i ON i.document_id = d.id "
            + "JOIN entity_continents ec ON ec.entity_id = e.id "
            + "WHERE d.quiz_eligible = 1 "
            + "AND e.review_status = 'VERIFIED' "
            + "AND d.review_status = 'VERIFIED' "
            + "AND d.document_type <> 'UNKNOWN' "
            + "AND i.review_status = 'VERIFIED' "
            + "AND d.duplicate_of_document_id IS NULL "
            + "AND i.duplicate_of_image_id IS NULL "
            + "ORDER BY d.id, i.id, ec.sort_order";

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

    @Override
    public List<QuizCandidate> findQuizEligibleCandidates() {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_QUIZ_CANDIDATES);
             ResultSet resultSet = statement.executeQuery()) {
            Map<String, CandidateAccumulator> candidates = new LinkedHashMap<>();
            while (resultSet.next()) {
                String key = resultSet.getString("document_id") + '\0'
                        + resultSet.getString("image_id");
                CandidateAccumulator candidate = candidates.computeIfAbsent(
                        key,
                        ignored -> candidate(resultSet)
                );
                candidate.continentIds.add(resultSet.getString("continent_id"));
            }
            return candidates.values().stream()
                    .map(CandidateAccumulator::toCandidate)
                    .toList();
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot read quiz-eligible catalogue candidates", exception);
        }
    }

    private static PassportDocument mapDocument(ResultSet resultSet) throws SQLException {
        return new PassportDocument(
                resultSet.getString("id"),
                resultSet.getString("entity_id"),
                DocumentType.valueOf(resultSet.getString("document_type")),
                resultSet.getString("title_uk"),
                resultSet.getString("variant"),
                resultSet.getString("source_relative_path"),
                ReviewStatus.valueOf(resultSet.getString("review_status")),
                resultSet.getInt("image_count")
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

    private static CandidateAccumulator candidate(ResultSet resultSet) {
        try {
            return new CandidateAccumulator(
                    resultSet.getString("document_id"),
                    resultSet.getString("entity_id"),
                    resultSet.getString("entity_display_name"),
                    DocumentType.valueOf(resultSet.getString("document_type")),
                    resultSet.getString("document_title"),
                    resultSet.getString("image_id"),
                    resultSet.getString("image_relative_path"),
                    resultSet.getString("image_sha256"),
                    ReviewStatus.valueOf(resultSet.getString("entity_review_status")),
                    ReviewStatus.valueOf(resultSet.getString("document_review_status")),
                    ReviewStatus.valueOf(resultSet.getString("image_review_status")),
                    resultSet.getInt("quiz_eligible") == 1,
                    resultSet.getString("canonical_document_id"),
                    resultSet.getString("canonical_image_id")
            );
        } catch (SQLException exception) {
            throw new RepositoryAccessException("Cannot map quiz-eligible catalogue candidate", exception);
        }
    }

    private static final class CandidateAccumulator {

        private final String documentId;
        private final String entityId;
        private final String entityDisplayName;
        private final DocumentType documentType;
        private final String documentTitle;
        private final String imageId;
        private final String imageRelativePath;
        private final String imageSha256;
        private final ReviewStatus entityReviewStatus;
        private final ReviewStatus documentReviewStatus;
        private final ReviewStatus imageReviewStatus;
        private final boolean quizEligible;
        private final String canonicalDocumentId;
        private final String canonicalImageId;
        private final LinkedHashSet<String> continentIds = new LinkedHashSet<>();

        private CandidateAccumulator(
                String documentId,
                String entityId,
                String entityDisplayName,
                DocumentType documentType,
                String documentTitle,
                String imageId,
                String imageRelativePath,
                String imageSha256,
                ReviewStatus entityReviewStatus,
                ReviewStatus documentReviewStatus,
                ReviewStatus imageReviewStatus,
                boolean quizEligible,
                String canonicalDocumentId,
                String canonicalImageId
        ) {
            this.documentId = documentId;
            this.entityId = entityId;
            this.entityDisplayName = entityDisplayName;
            this.documentType = documentType;
            this.documentTitle = documentTitle;
            this.imageId = imageId;
            this.imageRelativePath = imageRelativePath;
            this.imageSha256 = imageSha256;
            this.entityReviewStatus = entityReviewStatus;
            this.documentReviewStatus = documentReviewStatus;
            this.imageReviewStatus = imageReviewStatus;
            this.quizEligible = quizEligible;
            this.canonicalDocumentId = canonicalDocumentId;
            this.canonicalImageId = canonicalImageId;
        }

        private QuizCandidate toCandidate() {
            return new QuizCandidate(
                    documentId,
                    entityId,
                    entityDisplayName,
                    continentIds,
                    documentType,
                    documentTitle,
                    imageId,
                    imageRelativePath,
                    imageSha256,
                    entityReviewStatus,
                    documentReviewStatus,
                    imageReviewStatus,
                    quizEligible,
                    canonicalDocumentId,
                    canonicalImageId
            );
        }
    }
}
