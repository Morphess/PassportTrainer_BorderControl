package ua.passporttrainer.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.model.QuestionSet;
import ua.passporttrainer.model.QuizCandidate;
import ua.passporttrainer.model.QuizGenerationRequest;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.ReviewStatus;
import ua.passporttrainer.repository.PassportRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedQuestionSetProviderTest {

    @Test
    void delegatesOnceToCanonicalGenerationAndReturnsQuestionsWithoutSessionState() {
        CountingRepository repository = new CountingRepository();
        GeneratedQuestionSetProvider provider = new GeneratedQuestionSetProvider(
                new QuizGenerationService(repository, Clock.systemUTC())
        );
        QuizGenerationRequest request = new QuizGenerationRequest(
                41L,
                QuizSessionCoordinator.ANONYMOUS_USER_ID,
                QuizMode.EXAMINATION,
                1,
                2,
                Set.of(QuizQuestionType.DOCUMENT_TYPE),
                Set.of(),
                Set.of(),
                Set.of(),
                77L
        );

        QuestionSet questionSet = provider.provide(request);

        assertEquals(1, repository.readCount);
        assertEquals(1, questionSet.questions().size());
        assertEquals(2, questionSet.questions().getFirst().options().size());
        assertTrue(questionSet.assessmentProvenance().isEmpty());
    }

    private static final class CountingRepository implements PassportRepository {

        private int readCount;

        @Override
        public Optional<PassportDocument> findById(String id) {
            return Optional.empty();
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
            readCount++;
            return List.of(new QuizCandidate(
                    "document:1",
                    "entity:1",
                    "Entity 1",
                    Set.of("continent:europe"),
                    DocumentType.ORDINARY_PASSPORT,
                    "Document 1",
                    "image:1",
                    "fixtures/1.jpg",
                    "%064x".formatted(1),
                    ReviewStatus.VERIFIED,
                    ReviewStatus.VERIFIED,
                    ReviewStatus.VERIFIED,
                    true,
                    "document:1",
                    "image:1"
            ));
        }
    }
}
