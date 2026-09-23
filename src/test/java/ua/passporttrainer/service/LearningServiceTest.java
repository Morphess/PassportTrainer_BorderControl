package ua.passporttrainer.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.model.QuizCandidate;
import ua.passporttrainer.model.ReviewStatus;
import ua.passporttrainer.presentation.LearningDocumentPresentation;
import ua.passporttrainer.presentation.LearningEntityPresentation;
import ua.passporttrainer.repository.PassportRepository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningServiceTest {

    @Test
    void returnsOnlyUsableCanonicalKnownCandidates() {
        QuizCandidate usable = candidate(
                "document:usable", "entity:ukraine", "Україна",
                DocumentType.ORDINARY_PASSPORT, true,
                ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                "document:usable", "image:usable"
        );
        List<QuizCandidate> candidates = List.of(
                usable,
                candidate("document:disabled", "entity:ukraine", "Україна",
                        DocumentType.ORDINARY_PASSPORT, false,
                        ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                        "document:disabled", "image:disabled"),
                candidate("document:entity-unverified", "entity:other", "Інша",
                        DocumentType.ORDINARY_PASSPORT, true,
                        ReviewStatus.NEEDS_REVIEW, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                        "document:entity-unverified", "image:entity-unverified"),
                candidate("document:document-unverified", "entity:other", "Інша",
                        DocumentType.DIPLOMATIC_PASSPORT, true,
                        ReviewStatus.VERIFIED, ReviewStatus.AUTO_CLASSIFIED, ReviewStatus.VERIFIED,
                        "document:document-unverified", "image:document-unverified"),
                candidate("document:image-unverified", "entity:other", "Інша",
                        DocumentType.SERVICE_PASSPORT, true,
                        ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.NEEDS_REVIEW,
                        "document:image-unverified", "image:image-unverified"),
                candidate("document:unknown", "entity:other", "Інша",
                        DocumentType.UNKNOWN, true,
                        ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                        "document:unknown", "image:unknown"),
                candidate("document:duplicate", "entity:other", "Інша",
                        DocumentType.NATIONAL_ID, true,
                        ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                        "document:canonical", "image:duplicate"),
                candidate("document:image-duplicate", "entity:other", "Інша",
                        DocumentType.NATIONAL_ID, true,
                        ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                        "document:image-duplicate", "image:canonical")
        );
        LearningService service = new LearningService(new StubPassportRepository(candidates));

        List<LearningEntityPresentation> entities = service.findEligibleEntities();
        List<LearningDocumentPresentation> documents = service.findEligibleDocuments("entity:ukraine");

        assertAll(
                () -> assertEquals(List.of("entity:ukraine"),
                        entities.stream().map(LearningEntityPresentation::entityId).toList()),
                () -> assertEquals(1, entities.getFirst().documentCount()),
                () -> assertEquals(List.of("document:usable"),
                        documents.stream().map(LearningDocumentPresentation::documentId).toList())
        );
    }

    @Test
    void ordersEntitiesAndDocumentsDeterministicallyAndPreservesPresentationMetadata() {
        QuizCandidate service = candidate("document:service", "entity:ukraine", "Україна",
                DocumentType.SERVICE_PASSPORT, true,
                ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                "document:service", "image:service");
        QuizCandidate ordinary = candidate("document:ordinary", "entity:ukraine", "Україна",
                DocumentType.ORDINARY_PASSPORT, true,
                ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                "document:ordinary", "image:ordinary");
        QuizCandidate ireland = candidate("document:ireland", "entity:ireland", "Ірландія",
                DocumentType.NATIONAL_ID, true,
                ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                "document:ireland", "image:ireland");
        StubPassportRepository repository = new StubPassportRepository(List.of(service, ordinary, ireland));
        repository.documents.put("document:ordinary", document(ordinary, "Біометричний"));
        LearningService learningService = new LearningService(repository);

        List<LearningEntityPresentation> entities = learningService.findEligibleEntities();
        List<LearningDocumentPresentation> documents =
                learningService.findEligibleDocuments("entity:ukraine");

        assertAll(
                () -> assertEquals(List.of("Ірландія", "Україна"),
                        entities.stream().map(LearningEntityPresentation::displayName).toList()),
                () -> assertEquals(List.of(1, 2),
                        entities.stream().map(LearningEntityPresentation::documentCount).toList()),
                () -> assertEquals(List.of("document:ordinary", "document:service"),
                        documents.stream().map(LearningDocumentPresentation::documentId).toList()),
                () -> assertEquals("documentType.ORDINARY_PASSPORT",
                        documents.getFirst().documentTypeLabelKey()),
                () -> assertEquals("Біометричний", documents.getFirst().variant()),
                () -> assertTrue(documents.getFirst().hasMeaningfulVariant())
        );
    }

    @Test
    void returnsEmptyListsForAnEmptyPoolAndUnknownEntity() {
        LearningService empty = new LearningService(new StubPassportRepository(List.of()));
        LearningService populated = new LearningService(new StubPassportRepository(List.of(
                candidate("document:one", "entity:one", "Одна",
                        DocumentType.ORDINARY_PASSPORT, true,
                        ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                        "document:one", "image:one")
        )));

        assertAll(
                () -> assertTrue(empty.findEligibleEntities().isEmpty()),
                () -> assertTrue(empty.findEligibleDocuments("entity:none").isEmpty()),
                () -> assertTrue(populated.findEligibleDocuments("entity:none").isEmpty())
        );
    }

    private static QuizCandidate candidate(
            String documentId,
            String entityId,
            String entityName,
            DocumentType documentType,
            boolean eligible,
            ReviewStatus entityStatus,
            ReviewStatus documentStatus,
            ReviewStatus imageStatus,
            String canonicalDocumentId,
            String canonicalImageId
    ) {
        String suffix = documentId.substring(documentId.indexOf(':') + 1);
        String imageId = "image:" + suffix;
        return new QuizCandidate(
                documentId,
                entityId,
                entityName,
                Set.of("continent:europe"),
                documentType,
                "Назва " + suffix,
                imageId,
                "fixtures/" + suffix + ".jpg",
                "%064x".formatted(Math.abs(suffix.hashCode())),
                entityStatus,
                documentStatus,
                imageStatus,
                eligible,
                canonicalDocumentId,
                canonicalImageId
        );
    }

    private static PassportDocument document(QuizCandidate candidate, String variant) {
        return new PassportDocument(
                candidate.documentId(),
                candidate.entityId(),
                candidate.documentType(),
                candidate.documentTitle(),
                variant,
                candidate.imageRelativePath(),
                candidate.documentReviewStatus(),
                1
        );
    }

    private static final class StubPassportRepository implements PassportRepository {

        private final List<QuizCandidate> candidates;
        private final Map<String, PassportDocument> documents = new LinkedHashMap<>();

        private StubPassportRepository(List<QuizCandidate> candidates) {
            this.candidates = List.copyOf(candidates);
            for (QuizCandidate candidate : candidates) {
                documents.put(candidate.documentId(), document(candidate, ""));
            }
        }

        @Override
        public Optional<PassportDocument> findById(String id) {
            return Optional.ofNullable(documents.get(id));
        }

        @Override
        public List<PassportDocument> findByCountryId(String countryId) {
            return List.of();
        }

        @Override
        public List<PassportImage> findImagesByDocumentId(String passportDocumentId) {
            return List.of();
        }

        @Override
        public List<QuizCandidate> findQuizEligibleCandidates() {
            return candidates;
        }
    }
}
