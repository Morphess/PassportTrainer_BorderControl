package ua.passporttrainer.service;

import org.junit.jupiter.api.Test;
import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.model.QuizCandidate;
import ua.passporttrainer.model.QuizContent;
import ua.passporttrainer.model.QuizGenerationFailureReason;
import ua.passporttrainer.model.QuizGenerationRequest;
import ua.passporttrainer.model.QuizMode;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.model.ReviewStatus;
import ua.passporttrainer.repository.PassportRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizGenerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Set<QuizQuestionType> ALL_QUESTION_TYPES = Set.of(
            QuizQuestionType.PASSPORT_TO_COUNTRY,
            QuizQuestionType.COUNTRY_TO_PASSPORT,
            QuizQuestionType.DOCUMENT_TYPE,
            QuizQuestionType.FIND_ORDINARY_PASSPORT
    );

    @Test
    void fixedSeedProducesTheSameSessionFromAnyRepositoryOrder() {
        List<QuizCandidate> candidates = fixturePool();
        List<QuizCandidate> reversed = new ArrayList<>(candidates);
        Collections.reverse(reversed);
        CapturingPassportRepository firstRepository = new CapturingPassportRepository(candidates);
        CapturingPassportRepository secondRepository = new CapturingPassportRepository(reversed);
        AtomicLong observedSeed = new AtomicLong(Long.MIN_VALUE);
        QuizGenerationRequest request = request(8, 4, ALL_QUESTION_TYPES, 37L);

        QuizSession first = new QuizGenerationService(
                firstRepository,
                CLOCK,
                seed -> {
                    observedSeed.set(seed);
                    return new SplittableRandom(seed);
                }
        ).generate(request);
        QuizSession second = service(secondRepository).generate(request);

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(37L, observedSeed.get()),
                () -> assertEquals(1, firstRepository.quizCandidateQueries),
                () -> assertEquals(1, secondRepository.quizCandidateQueries),
                () -> assertEquals(NOW, first.startedAt())
        );
    }

    @Test
    void differentSeedsVaryQuestionSelectionOrOrdering() {
        Set<List<QuizQuestion>> generated = new HashSet<>();
        for (long seed = 0; seed < 8; seed++) {
            generated.add(service(fixturePool()).generate(
                    request(8, 4, ALL_QUESTION_TYPES, seed)
            ).questions());
        }

        assertTrue(generated.size() > 1);
    }

    @Test
    void generatedSessionHasRequestedCountUniqueIdsAndUniqueDocuments() {
        QuizSession session = service(fixturePool()).generate(
                request(12, 4, ALL_QUESTION_TYPES, 9L)
        );
        Set<Long> questionIds = new HashSet<>();
        Set<String> correctDocumentIds = new HashSet<>();
        Set<String> correctImageIds = new HashSet<>();

        for (QuizQuestion question : session.questions()) {
            assertQuestionInvariants(question, 4);
            assertTrue(questionIds.add(question.id()));
            QuizContent.Document correctDocument = correctDocument(question);
            assertTrue(correctDocumentIds.add(correctDocument.id()));
            assertTrue(correctImageIds.add(correctDocument.imageId()));
        }

        assertAll(
                () -> assertEquals(12, session.questions().size()),
                () -> assertEquals(LongStream.rangeClosed(1, 12).boxed().toList(),
                        session.questions().stream().map(QuizQuestion::id).toList()),
                () -> assertEquals(QuizMode.TRAINING, session.mode()),
                () -> assertTrue(session.answers().isEmpty()),
                () -> assertFalse(session.completed())
        );
    }

    @Test
    void correctAnswerPositionVariesAcrossSeeds() {
        Set<Integer> positions = new HashSet<>();
        for (long seed = 0; seed < 32; seed++) {
            QuizQuestion question = service(fixturePool()).generate(
                    request(1, 4, Set.of(QuizQuestionType.PASSPORT_TO_COUNTRY), seed)
            ).questions().getFirst();
            positions.add(optionIndex(question, question.correctAnswerId()));
        }

        assertTrue(positions.size() > 1);
    }

    @Test
    void filtersUnknownUnverifiedAndIneligibleCandidatesDefensively() {
        QuizCandidate valid = candidate(1, "ua", Set.of("continent:europe"),
                DocumentType.ORDINARY_PASSPORT);
        List<QuizCandidate> candidates = List.of(
                valid,
                candidate(2, "pl", Set.of("continent:europe"), DocumentType.UNKNOWN),
                candidate(3, "de", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT,
                        ReviewStatus.AUTO_CLASSIFIED, ReviewStatus.VERIFIED,
                        ReviewStatus.VERIFIED, true, null, null, null),
                candidate(4, "fr", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT,
                        ReviewStatus.VERIFIED, ReviewStatus.AUTO_CLASSIFIED,
                        ReviewStatus.VERIFIED, true, null, null, null),
                candidate(5, "jp", Set.of("continent:asia"), DocumentType.ORDINARY_PASSPORT,
                        ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                        ReviewStatus.NEEDS_REVIEW, true, null, null, null),
                candidate(6, "ke", Set.of("continent:africa"), DocumentType.ORDINARY_PASSPORT,
                        ReviewStatus.VERIFIED, ReviewStatus.VERIFIED,
                        ReviewStatus.VERIFIED, false, null, null, null)
        );

        QuizQuestion question = service(candidates).generate(
                request(1, 2, Set.of(QuizQuestionType.DOCUMENT_TYPE), 1L)
        ).questions().getFirst();

        assertEquals(valid.documentId(), ((QuizContent.Document) question.subject()).id());
    }

    @Test
    void emptyEligiblePoolReturnsStructuredFailure() {
        QuizGenerationException exception = assertThrows(
                QuizGenerationException.class,
                () -> service(List.of()).generate(
                        request(1, 2, Set.of(QuizQuestionType.DOCUMENT_TYPE), 1L)
                )
        );

        assertAll(
                () -> assertEquals(QuizGenerationFailureReason.INSUFFICIENT_ELIGIBLE_DATA,
                        exception.reason()),
                () -> assertEquals(1, exception.requestedQuestionCount()),
                () -> assertEquals(0, exception.availableCandidateCount()),
                () -> assertTrue(exception.questionType().isEmpty())
        );
    }

    @Test
    void passportToCountryFailsWithoutEnoughDistinctCountryDistractors() {
        List<QuizCandidate> candidates = List.of(
                candidate(1, "ua", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(2, "pl", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(3, "de", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT)
        );

        QuizGenerationException exception = assertThrows(
                QuizGenerationException.class,
                () -> service(candidates).generate(
                        request(1, 4, Set.of(QuizQuestionType.PASSPORT_TO_COUNTRY), 1L)
                )
        );

        assertAll(
                () -> assertEquals(QuizGenerationFailureReason.INSUFFICIENT_DISTRACTORS,
                        exception.reason()),
                () -> assertEquals(Optional.of(QuizQuestionType.PASSPORT_TO_COUNTRY),
                        exception.questionType()),
                () -> assertEquals(3, exception.availableCandidateCount())
        );
    }

    @Test
    void countryToPassportFailsWithoutEnoughDistinctDocumentEntities() {
        List<QuizCandidate> candidates = List.of(
                candidate(1, "ua", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(2, "pl", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(3, "de", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT)
        );

        QuizGenerationException exception = assertThrows(
                QuizGenerationException.class,
                () -> service(candidates).generate(
                        request(1, 4, Set.of(QuizQuestionType.COUNTRY_TO_PASSPORT), 2L)
                )
        );

        assertAll(
                () -> assertEquals(QuizGenerationFailureReason.INSUFFICIENT_DISTRACTORS,
                        exception.reason()),
                () -> assertEquals(Optional.of(QuizQuestionType.COUNTRY_TO_PASSPORT),
                        exception.questionType())
        );
    }

    @Test
    void generatesPassportToCountryWithSameContinentPreference() {
        List<QuizCandidate> candidates = List.of(
                candidate(1, "ua", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(2, "pl", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(3, "de", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(4, "fr", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(5, "jp", Set.of("continent:asia"), DocumentType.ORDINARY_PASSPORT),
                candidate(6, "kr", Set.of("continent:asia"), DocumentType.ORDINARY_PASSPORT),
                candidate(7, "cn", Set.of("continent:asia"), DocumentType.ORDINARY_PASSPORT),
                candidate(8, "th", Set.of("continent:asia"), DocumentType.ORDINARY_PASSPORT)
        );
        QuizQuestion question = service(candidates).generate(
                request(1, 4, Set.of(QuizQuestionType.PASSPORT_TO_COUNTRY), 5L)
        ).questions().getFirst();
        QuizContent.Document subject = (QuizContent.Document) question.subject();
        Set<String> subjectContinents = candidates.stream()
                .filter(candidate -> candidate.documentId().equals(subject.id()))
                .findFirst()
                .orElseThrow()
                .continentIds();

        assertAll(
                () -> assertEquals(QuizQuestionType.PASSPORT_TO_COUNTRY, question.type()),
                () -> assertEquals(subject.entityId(), question.correctAnswerId()),
                () -> assertTrue(question.options().stream()
                        .allMatch(QuizContent.Country.class::isInstance)),
                () -> assertTrue(question.options().stream()
                        .map(QuizContent.Country.class::cast)
                        .allMatch(option -> candidates.stream()
                                .filter(candidate -> candidate.entityId().equals(option.id()))
                                .anyMatch(candidate -> !Collections.disjoint(
                                        subjectContinents, candidate.continentIds()
                                ))))
        );
    }

    @Test
    void passportToCountryFallsBackToGlobalDistractors() {
        List<QuizCandidate> candidates = List.of(
                candidate(1, "ua", Set.of("continent:one"), DocumentType.ORDINARY_PASSPORT),
                candidate(2, "pl", Set.of("continent:two"), DocumentType.ORDINARY_PASSPORT),
                candidate(3, "de", Set.of("continent:three"), DocumentType.ORDINARY_PASSPORT),
                candidate(4, "fr", Set.of("continent:four"), DocumentType.ORDINARY_PASSPORT)
        );

        QuizQuestion question = service(candidates).generate(
                request(1, 4, Set.of(QuizQuestionType.PASSPORT_TO_COUNTRY), 5L)
        ).questions().getFirst();

        assertQuestionInvariants(question, 4);
    }

    @Test
    void generatesCountryToPassportWithDistinctSameTypeEntities() {
        QuizQuestion question = service(fixturePool()).generate(
                request(1, 4, Set.of(QuizQuestionType.COUNTRY_TO_PASSPORT), 11L)
        ).questions().getFirst();
        List<QuizContent.Document> options = question.options().stream()
                .map(QuizContent.Document.class::cast)
                .toList();
        QuizContent.Document correct = correctDocument(question);

        assertAll(
                () -> assertEquals(QuizQuestionType.COUNTRY_TO_PASSPORT, question.type()),
                () -> assertEquals(4, options.stream().map(QuizContent.Document::entityId).distinct().count()),
                () -> assertTrue(options.stream()
                        .allMatch(document -> document.documentType() == correct.documentType())),
                () -> assertEquals(((QuizContent.Country) question.subject()).id(), correct.entityId())
        );
    }

    @Test
    void generatesDocumentTypeQuestionWithKnownTypeOptions() {
        QuizQuestion question = service(fixturePool()).generate(
                request(1, 4, Set.of(QuizQuestionType.DOCUMENT_TYPE), 13L)
        ).questions().getFirst();
        QuizContent.Document subject = (QuizContent.Document) question.subject();

        assertAll(
                () -> assertEquals(QuizQuestionType.DOCUMENT_TYPE, question.type()),
                () -> assertEquals(subject.documentType().name(), question.correctAnswerId()),
                () -> assertEquals(Set.of(
                                DocumentType.ORDINARY_PASSPORT,
                                DocumentType.DIPLOMATIC_PASSPORT,
                                DocumentType.SERVICE_PASSPORT,
                                DocumentType.NATIONAL_ID
                        ), question.options().stream()
                                .map(QuizContent.DocumentTypeValue.class::cast)
                                .map(QuizContent.DocumentTypeValue::documentType)
                                .collect(java.util.stream.Collectors.toSet())),
                () -> assertFalse(question.options().stream()
                        .map(QuizContent.DocumentTypeValue.class::cast)
                        .anyMatch(option -> option.documentType() == DocumentType.UNKNOWN))
        );
    }

    @Test
    void generatesFindOrdinaryQuestionWithExactlyOneOrdinaryOption() {
        QuizQuestion question = service(fixturePool()).generate(
                request(1, 4, Set.of(QuizQuestionType.FIND_ORDINARY_PASSPORT), 17L)
        ).questions().getFirst();
        List<QuizContent.Document> options = question.options().stream()
                .map(QuizContent.Document.class::cast)
                .toList();

        assertAll(
                () -> assertEquals(QuizQuestionType.FIND_ORDINARY_PASSPORT, question.type()),
                () -> assertEquals(DocumentType.ORDINARY_PASSPORT,
                        ((QuizContent.DocumentTypeValue) question.subject()).documentType()),
                () -> assertEquals(1, options.stream()
                        .filter(option -> option.documentType() == DocumentType.ORDINARY_PASSPORT)
                        .count()),
                () -> assertEquals(DocumentType.ORDINARY_PASSPORT,
                        correctDocument(question).documentType())
        );
    }

    @Test
    void excludesDuplicatesWithoutReservingDiscardedDocumentImages() {
        QuizCandidate canonical = candidate(1, "ua", Set.of("continent:europe"),
                DocumentType.ORDINARY_PASSPORT);
        QuizCandidate linkedDuplicate = candidate(
                25, "ua", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT,
                ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, true,
                canonical.documentId(), canonical.imageId(), canonical.imageSha256()
        );
        QuizCandidate exactImageDuplicate = candidate(
                26, "ca", Set.of("continent:north-america"), DocumentType.ORDINARY_PASSPORT,
                ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, ReviewStatus.VERIFIED, true,
                null, null, canonical.imageSha256()
        );
        List<QuizCandidate> candidates = new ArrayList<>(fixturePool());
        candidates.add(linkedDuplicate);
        candidates.add(exactImageDuplicate);

        QuizSession session = service(candidates).generate(
                request(8, 4, ALL_QUESTION_TYPES, 23L)
        );
        Set<String> visibleDocumentIds = new HashSet<>();
        for (QuizQuestion question : session.questions()) {
            if (question.subject() instanceof QuizContent.Document document) {
                visibleDocumentIds.add(document.id());
            }
            question.options().stream()
                    .filter(QuizContent.Document.class::isInstance)
                    .map(QuizContent.Document.class::cast)
                    .map(QuizContent.Document::id)
                    .forEach(visibleDocumentIds::add);
        }

        assertAll(
                () -> assertFalse(visibleDocumentIds.contains(linkedDuplicate.documentId())),
                () -> assertFalse(visibleDocumentIds.contains(exactImageDuplicate.documentId()))
        );

        QuizCandidate firstImage = candidate("document:1", "image:1", "ua", 101);
        QuizCandidate discardedSecondImage = candidate("document:1", "image:2", "ua", 102);
        QuizCandidate independentMatchingImage = candidate("document:2", "image:3", "pl", 102);
        QuizSession multiImageSession = service(List.of(
                firstImage, discardedSecondImage, independentMatchingImage
        )).generate(request(2, 2, Set.of(QuizQuestionType.DOCUMENT_TYPE), 3L));

        assertEquals(
                Set.of("document:1", "document:2"),
                multiImageSession.questions().stream()
                        .map(QuizGenerationServiceTest::correctDocument)
                        .map(QuizContent.Document::id)
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void appliesAlternateContinentMembershipScope() {
        QuizCandidate transcontinental = candidate(1, "tr", Set.of(
                "continent:europe", "continent:asia"
        ), DocumentType.ORDINARY_PASSPORT);

        QuizQuestion question = service(List.of(transcontinental)).generate(request(
                1,
                2,
                Set.of(QuizQuestionType.DOCUMENT_TYPE),
                1L,
                Set.of("continent:asia"),
                Set.of(),
                Set.of()
        )).questions().getFirst();

        assertEquals(transcontinental.documentId(),
                ((QuizContent.Document) question.subject()).id());
    }

    @Test
    void appliesEntityScopeBeforeGeneration() {
        QuizSession session = service(fixturePool()).generate(request(
                4,
                2,
                Set.of(QuizQuestionType.DOCUMENT_TYPE),
                29L,
                Set.of(),
                Set.of("entity:ua"),
                Set.of()
        ));

        assertTrue(session.questions().stream()
                .map(QuizQuestion::subject)
                .map(QuizContent.Document.class::cast)
                .allMatch(document -> document.entityId().equals("entity:ua")));
    }

    @Test
    void appliesDocumentTypeScopeBeforeGeneration() {
        QuizSession session = service(fixturePool()).generate(request(
                4,
                4,
                Set.of(QuizQuestionType.PASSPORT_TO_COUNTRY),
                31L,
                Set.of(),
                Set.of(),
                Set.of(DocumentType.ORDINARY_PASSPORT)
        ));

        assertTrue(session.questions().stream()
                .map(QuizQuestion::subject)
                .map(QuizContent.Document.class::cast)
                .allMatch(document -> document.documentType() == DocumentType.ORDINARY_PASSPORT));
    }

    @Test
    void rejectsInvalidGenerationParameters() {
        QuizGenerationException excessiveTypeOptions = assertThrows(
                QuizGenerationException.class,
                () -> service(fixturePool()).generate(
                        request(1, 5, Set.of(QuizQuestionType.DOCUMENT_TYPE), 1L)
                )
        );

        assertAll(
                () -> assertFailureReason(
                        request(0, 4, ALL_QUESTION_TYPES, 1L),
                        QuizGenerationFailureReason.INVALID_REQUEST
                ),
                () -> assertFailureReason(
                        request(1, 1, ALL_QUESTION_TYPES, 1L),
                        QuizGenerationFailureReason.INVALID_REQUEST
                ),
                () -> assertFailureReason(
                        request(1, 4, Set.of(), 1L),
                        QuizGenerationFailureReason.INVALID_REQUEST
                ),
                () -> assertFailureReason(
                        request(
                                1, 2, Set.of(QuizQuestionType.DOCUMENT_TYPE), 1L,
                                Set.of(), Set.of(), Set.of(DocumentType.UNKNOWN)
                        ),
                        QuizGenerationFailureReason.INVALID_REQUEST
                ),
                () -> assertEquals(
                        QuizGenerationFailureReason.INVALID_REQUEST,
                        excessiveTypeOptions.reason()
                ),
                () -> assertEquals(0, excessiveTypeOptions.availableCandidateCount())
        );
    }

    @Test
    void rejectsOversizedOrSemanticallyUnsatisfiableSessionsWithoutPartialResults() {
        QuizGenerationException exception = assertThrows(
                QuizGenerationException.class,
                () -> service(fixturePool().subList(0, 3)).generate(
                        request(4, 2, Set.of(QuizQuestionType.DOCUMENT_TYPE), 2L)
                )
        );
        List<QuizCandidate> twoEntities = List.of(
                candidate(1, "ua", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(2, "ua", Set.of("continent:europe"), DocumentType.DIPLOMATIC_PASSPORT),
                candidate(3, "pl", Set.of("continent:europe"), DocumentType.ORDINARY_PASSPORT),
                candidate(4, "pl", Set.of("continent:europe"), DocumentType.DIPLOMATIC_PASSPORT)
        );
        QuizGenerationException insufficientUniqueQuestions = assertThrows(
                QuizGenerationException.class,
                () -> service(twoEntities).generate(
                        request(3, 2, Set.of(QuizQuestionType.COUNTRY_TO_PASSPORT), 2L)
                )
        );


        assertAll(
                () -> assertEquals(QuizGenerationFailureReason.INSUFFICIENT_ELIGIBLE_DATA,
                        exception.reason()),
                () -> assertEquals(3, exception.availableCandidateCount()),
                () -> assertEquals(QuizGenerationFailureReason.INSUFFICIENT_ELIGIBLE_DATA,
                        insufficientUniqueQuestions.reason()),
                () -> assertEquals(4, insufficientUniqueQuestions.availableCandidateCount())
        );
    }

    @Test
    void repeatedSeedsPreserveAllGeneratedQuestionInvariants() {
        for (long seed = 0; seed < 128; seed++) {
            QuizSession session = service(fixturePool()).generate(
                    request(8, 4, ALL_QUESTION_TYPES, seed)
            );
            Set<String> documents = new LinkedHashSet<>();
            for (QuizQuestion question : session.questions()) {
                assertQuestionInvariants(question, 4);
                assertTrue(documents.add(correctDocument(question).id()));
            }
        }
    }

    private static void assertFailureReason(
            QuizGenerationRequest request,
            QuizGenerationFailureReason expected
    ) {
        QuizGenerationException exception = assertThrows(
                QuizGenerationException.class,
                () -> service(fixturePool()).generate(request)
        );
        assertEquals(expected, exception.reason());
    }

    private static void assertQuestionInvariants(QuizQuestion question, int optionCount) {
        Set<String> optionIds = new HashSet<>();
        question.options().forEach(option -> assertTrue(optionIds.add(option.id())));
        assertAll(
                () -> assertEquals(optionCount, question.options().size()),
                () -> assertEquals(1, question.options().stream()
                        .filter(option -> option.id().equals(question.correctAnswerId()))
                        .count()),
                () -> assertTrue(question.hasOption(question.correctAnswerId()))
        );
    }

    private static int optionIndex(QuizQuestion question, String optionId) {
        for (int index = 0; index < question.options().size(); index++) {
            if (question.options().get(index).id().equals(optionId)) {
                return index;
            }
        }
        throw new AssertionError("Missing option: " + optionId);
    }

    private static QuizContent.Document correctDocument(QuizQuestion question) {
        return switch (question.type()) {
            case PASSPORT_TO_COUNTRY, DOCUMENT_TYPE ->
                    (QuizContent.Document) question.subject();
            case COUNTRY_TO_PASSPORT, FIND_ORDINARY_PASSPORT -> question.options().stream()
                    .filter(QuizContent.Document.class::isInstance)
                    .map(QuizContent.Document.class::cast)
                    .filter(document -> document.id().equals(question.correctAnswerId()))
                    .findFirst()
                    .orElseThrow();
        };
    }


    private static QuizGenerationService service(List<QuizCandidate> candidates) {
        return service(new CapturingPassportRepository(candidates));
    }

    private static QuizGenerationService service(PassportRepository repository) {
        return new QuizGenerationService(repository, CLOCK);
    }

    private static QuizGenerationRequest request(
            int questionCount,
            int optionCount,
            Set<QuizQuestionType> questionTypes,
            long seed
    ) {
        return request(
                questionCount, optionCount, questionTypes, seed,
                Set.of(), Set.of(), Set.of()
        );
    }

    private static QuizGenerationRequest request(
            int questionCount,
            int optionCount,
            Set<QuizQuestionType> questionTypes,
            long seed,
            Set<String> continentIds,
            Set<String> entityIds,
            Set<DocumentType> documentTypes
    ) {
        return new QuizGenerationRequest(
                100,
                7,
                QuizMode.TRAINING,
                questionCount,
                optionCount,
                questionTypes,
                continentIds,
                entityIds,
                documentTypes,
                seed
        );
    }

    private static List<QuizCandidate> fixturePool() {
        List<EntityFixture> entities = List.of(
                new EntityFixture("ua", Set.of("continent:europe")),
                new EntityFixture("pl", Set.of("continent:europe")),
                new EntityFixture("de", Set.of("continent:europe")),
                new EntityFixture("fr", Set.of("continent:europe")),
                new EntityFixture("jp", Set.of("continent:asia")),
                new EntityFixture("ke", Set.of("continent:africa"))
        );
        List<QuizCandidate> candidates = new ArrayList<>();
        int id = 1;
        for (EntityFixture entity : entities) {
            for (DocumentType documentType : List.of(
                    DocumentType.ORDINARY_PASSPORT,
                    DocumentType.DIPLOMATIC_PASSPORT,
                    DocumentType.SERVICE_PASSPORT,
                    DocumentType.NATIONAL_ID
            )) {
                candidates.add(candidate(id++, entity.code(), entity.continentIds(), documentType));
            }
        }
        return List.copyOf(candidates);
    }

    private static QuizCandidate candidate(
            String documentId,
            String imageId,
            String entityCode,
            int shaValue
    ) {
        return new QuizCandidate(
                documentId,
                "entity:" + entityCode,
                entityCode.toUpperCase(),
                Set.of("continent:europe"),
                DocumentType.ORDINARY_PASSPORT,
                "Ordinary passport " + entityCode,
                imageId,
                "fixtures/" + imageId.substring(imageId.indexOf(':') + 1) + ".jpg",
                "%064x".formatted(shaValue),
                ReviewStatus.VERIFIED,
                ReviewStatus.VERIFIED,
                ReviewStatus.VERIFIED,
                true,
                documentId,
                imageId
        );
    }

    private static QuizCandidate candidate(
            int id,
            String entityCode,
            Set<String> continentIds,
            DocumentType documentType
    ) {
        return candidate(
                id,
                entityCode,
                continentIds,
                documentType,
                ReviewStatus.VERIFIED,
                ReviewStatus.VERIFIED,
                ReviewStatus.VERIFIED,
                true,
                null,
                null,
                null
        );
    }

    private static QuizCandidate candidate(
            int id,
            String entityCode,
            Set<String> continentIds,
            DocumentType documentType,
            ReviewStatus entityStatus,
            ReviewStatus documentStatus,
            ReviewStatus imageStatus,
            boolean quizEligible,
            String canonicalDocumentId,
            String canonicalImageId,
            String imageSha256
    ) {
        String documentId = "document:" + id;
        String imageId = "image:" + id;
        return new QuizCandidate(
                documentId,
                "entity:" + entityCode,
                entityCode.toUpperCase(),
                continentIds,
                documentType,
                documentType.name() + ' ' + entityCode,
                imageId,
                "fixtures/" + id + ".jpg",
                imageSha256 == null ? "%064x".formatted(id) : imageSha256,
                entityStatus,
                documentStatus,
                imageStatus,
                quizEligible,
                canonicalDocumentId == null ? documentId : canonicalDocumentId,
                canonicalImageId == null ? imageId : canonicalImageId
        );
    }

    private record EntityFixture(String code, Set<String> continentIds) {
    }

    private static final class CapturingPassportRepository implements PassportRepository {

        private final List<QuizCandidate> candidates;
        private int quizCandidateQueries;

        private CapturingPassportRepository(List<QuizCandidate> candidates) {
            this.candidates = List.copyOf(candidates);
        }

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
            quizCandidateQueries++;
            return candidates;
        }
    }
}
