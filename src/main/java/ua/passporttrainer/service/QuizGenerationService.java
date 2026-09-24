package ua.passporttrainer.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.LongFunction;
import java.util.random.RandomGenerator;

import ua.passporttrainer.model.DocumentType;
import ua.passporttrainer.model.QuizCandidate;
import ua.passporttrainer.model.QuizContent;
import ua.passporttrainer.model.QuizGenerationFailureReason;
import ua.passporttrainer.model.QuizGenerationRequest;
import ua.passporttrainer.model.QuizQuestion;
import ua.passporttrainer.model.QuestionSet;
import ua.passporttrainer.model.QuizQuestionType;
import ua.passporttrainer.model.QuizSession;
import ua.passporttrainer.repository.PassportRepository;

/** Generates validated quiz sessions from canonical eligible catalogue candidates. */
public final class QuizGenerationService {

    private static final List<DocumentType> KNOWN_DOCUMENT_TYPES = List.of(
            DocumentType.ORDINARY_PASSPORT,
            DocumentType.DIPLOMATIC_PASSPORT,
            DocumentType.SERVICE_PASSPORT,
            DocumentType.NATIONAL_ID
    );
    private static final Comparator<QuizCandidate> CANDIDATE_ORDER = Comparator
            .comparing((QuizCandidate candidate) ->
                    !candidate.documentId().equals(candidate.canonicalDocumentId()))
            .thenComparing(QuizCandidate::documentId)
            .thenComparing(QuizCandidate::imageId);

    private final PassportRepository passportRepository;
    private final Clock clock;
    private final LongFunction<? extends RandomGenerator> randomFactory;

    public QuizGenerationService(PassportRepository passportRepository, Clock clock) {
        this(passportRepository, clock, SplittableRandom::new);
    }

    QuizGenerationService(
            PassportRepository passportRepository,
            Clock clock,
            LongFunction<? extends RandomGenerator> randomFactory
    ) {
        this.passportRepository = Objects.requireNonNull(passportRepository, "passportRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.randomFactory = Objects.requireNonNull(randomFactory, "randomFactory");
    }

    public QuizSession generate(QuizGenerationRequest request) {
        QuestionSet questionSet = generateQuestionSet(request);
        return new QuizSession(
                request.sessionId(),
                request.userId(),
                request.mode(),
                clock.instant(),
                null,
                questionSet.questions(),
                List.of()
        );
    }

    QuestionSet generateQuestionSet(QuizGenerationRequest request) {
        Objects.requireNonNull(request, "request");
        validateRequest(request);
        RandomGenerator random = Objects.requireNonNull(
                randomFactory.apply(request.randomSeed()),
                "randomFactory result"
        );
        List<QuizCandidate> candidates = eligibleCandidates(request);
        if (candidates.isEmpty()) {
            throw failure(
                    QuizGenerationFailureReason.INSUFFICIENT_ELIGIBLE_DATA,
                    "No eligible quiz candidates satisfy the request",
                    request,
                    0,
                    null
            );
        }
        if (candidates.size() < request.questionCount()) {
            throw failure(
                    QuizGenerationFailureReason.INSUFFICIENT_ELIGIBLE_DATA,
                    "The unique eligible document pool is smaller than the requested session",
                    request,
                    candidates.size(),
                    null
            );
        }

        List<Blueprint> blueprints = blueprints(request, candidates);
        shuffle(blueprints, random);
        List<QuizQuestion> questions = new ArrayList<>(request.questionCount());
        Set<String> usedQuestionKeys = new LinkedHashSet<>();
        Set<String> usedCanonicalDocuments = new LinkedHashSet<>();
        Set<String> usedImageHashes = new LinkedHashSet<>();
        QuizGenerationException firstDistractorFailure = null;

        for (Blueprint blueprint : blueprints) {
            QuizCandidate candidate = blueprint.candidate();
            String questionKey = questionKey(blueprint);
            if (usedQuestionKeys.contains(questionKey)
                    || usedCanonicalDocuments.contains(candidate.canonicalDocumentId())
                    || usedImageHashes.contains(candidate.imageSha256())) {
                continue;
            }
            try {
                QuizQuestion question = createQuestion(
                        questions.size() + 1L,
                        blueprint,
                        candidates,
                        request,
                        random
                );
                questions.add(question);
                usedQuestionKeys.add(questionKey);
                usedCanonicalDocuments.add(candidate.canonicalDocumentId());
                usedImageHashes.add(candidate.imageSha256());
                if (questions.size() == request.questionCount()) {
                    break;
                }
            } catch (QuizGenerationException exception) {
                if (exception.reason() != QuizGenerationFailureReason.INSUFFICIENT_DISTRACTORS) {
                    throw exception;
                }
                if (firstDistractorFailure == null) {
                    firstDistractorFailure = exception;
                }
            }
        }

        if (questions.size() != request.questionCount()) {
            if (firstDistractorFailure != null) {
                throw firstDistractorFailure;
            }
            throw failure(
                    QuizGenerationFailureReason.INSUFFICIENT_ELIGIBLE_DATA,
                    "The scoped pool cannot provide enough unique questions",
                    request,
                    candidates.size(),
                    null
            );
        }

        return QuestionSet.generated(questions);
    }

    private List<QuizCandidate> eligibleCandidates(QuizGenerationRequest request) {
        List<QuizCandidate> ordered = passportRepository.findQuizEligibleCandidates().stream()
                .filter(QuizCandidate::usable)
                .filter(candidate -> withinScope(candidate, request))
                .sorted(CANDIDATE_ORDER)
                .toList();
        Map<String, QuizCandidate> byCanonicalDocument = new LinkedHashMap<>();
        Set<String> imageHashes = new LinkedHashSet<>();
        for (QuizCandidate candidate : ordered) {
            if (byCanonicalDocument.containsKey(candidate.canonicalDocumentId())
                    || imageHashes.contains(candidate.imageSha256())) {
                continue;
            }
            byCanonicalDocument.put(candidate.canonicalDocumentId(), candidate);
            imageHashes.add(candidate.imageSha256());
        }
        return List.copyOf(byCanonicalDocument.values());
    }

    private static boolean withinScope(QuizCandidate candidate, QuizGenerationRequest request) {
        return (request.continentIds().isEmpty()
                || !Collections.disjoint(candidate.continentIds(), request.continentIds()))
                && (request.entityIds().isEmpty()
                || request.entityIds().contains(candidate.entityId()))
                && (request.documentTypes().isEmpty()
                || request.documentTypes().contains(candidate.documentType()));
    }

    private static List<Blueprint> blueprints(
            QuizGenerationRequest request,
            List<QuizCandidate> candidates
    ) {
        List<QuizQuestionType> types = request.allowedQuestionTypes().stream().sorted().toList();
        List<Blueprint> blueprints = new ArrayList<>(candidates.size() * types.size());
        for (QuizCandidate candidate : candidates) {
            for (QuizQuestionType type : types) {
                if (type != QuizQuestionType.FIND_ORDINARY_PASSPORT
                        || candidate.documentType() == DocumentType.ORDINARY_PASSPORT) {
                    blueprints.add(new Blueprint(type, candidate));
                }
            }
        }
        return blueprints;
    }

    private QuizQuestion createQuestion(
            long questionId,
            Blueprint blueprint,
            List<QuizCandidate> candidates,
            QuizGenerationRequest request,
            RandomGenerator random
    ) {
        return switch (blueprint.type()) {
            case PASSPORT_TO_COUNTRY -> passportToCountry(
                    questionId, blueprint.candidate(), candidates, request, random
            );
            case COUNTRY_TO_PASSPORT -> countryToPassport(
                    questionId, blueprint.candidate(), candidates, request, random
            );
            case DOCUMENT_TYPE -> documentType(
                    questionId, blueprint.candidate(), request, random
            );
            case FIND_ORDINARY_PASSPORT -> findOrdinaryPassport(
                    questionId, blueprint.candidate(), candidates, request, random
            );
        };
    }

    private QuizQuestion passportToCountry(
            long questionId,
            QuizCandidate correct,
            List<QuizCandidate> candidates,
            QuizGenerationRequest request,
            RandomGenerator random
    ) {
        Map<String, QuizCandidate> entities = new LinkedHashMap<>();
        for (QuizCandidate candidate : candidates) {
            if (!candidate.entityId().equals(correct.entityId())) {
                entities.putIfAbsent(candidate.entityId(), candidate);
            }
        }
        List<QuizCandidate> sameContinent = new ArrayList<>();
        List<QuizCandidate> otherContinents = new ArrayList<>();
        for (QuizCandidate candidate : entities.values()) {
            if (Collections.disjoint(candidate.continentIds(), correct.continentIds())) {
                otherContinents.add(candidate);
            } else {
                sameContinent.add(candidate);
            }
        }
        shuffle(sameContinent, random);
        shuffle(otherContinents, random);
        sameContinent.addAll(otherContinents);
        requireDistractors(
                sameContinent.size(), request.optionCount() - 1,
                QuizQuestionType.PASSPORT_TO_COUNTRY, request, candidates.size()
        );

        List<QuizContent> options = new ArrayList<>(request.optionCount());
        options.add(new QuizContent.Country(correct.entityId(), correct.entityDisplayName()));
        for (int index = 0; index < request.optionCount() - 1; index++) {
            QuizCandidate distractor = sameContinent.get(index);
            options.add(new QuizContent.Country(
                    distractor.entityId(), distractor.entityDisplayName()
            ));
        }
        shuffle(options, random);
        return new QuizQuestion(
                questionId,
                QuizQuestionType.PASSPORT_TO_COUNTRY,
                correct.documentContent(),
                options,
                correct.entityId()
        );
    }

    private QuizQuestion countryToPassport(
            long questionId,
            QuizCandidate correct,
            List<QuizCandidate> candidates,
            QuizGenerationRequest request,
            RandomGenerator random
    ) {
        List<QuizCandidate> sameType = new ArrayList<>();
        List<QuizCandidate> otherTypes = new ArrayList<>();
        for (QuizCandidate candidate : candidates) {
            if (candidate.entityId().equals(correct.entityId())
                    || candidate.imageSha256().equals(correct.imageSha256())) {
                continue;
            }
            if (candidate.documentType() == correct.documentType()) {
                sameType.add(candidate);
            } else {
                otherTypes.add(candidate);
            }
        }
        shuffle(sameType, random);
        shuffle(otherTypes, random);
        sameType.addAll(otherTypes);
        List<QuizCandidate> distractors = distinctEntities(
                sameType, request.optionCount() - 1
        );
        requireDistractors(
                distractors.size(), request.optionCount() - 1,
                QuizQuestionType.COUNTRY_TO_PASSPORT, request, candidates.size()
        );

        List<QuizContent> options = new ArrayList<>(request.optionCount());
        options.add(correct.documentContent());
        distractors.forEach(candidate -> options.add(candidate.documentContent()));
        shuffle(options, random);
        return new QuizQuestion(
                questionId,
                QuizQuestionType.COUNTRY_TO_PASSPORT,
                new QuizContent.Country(correct.entityId(), correct.entityDisplayName()),
                options,
                correct.documentId()
        );
    }

    private QuizQuestion documentType(
            long questionId,
            QuizCandidate correct,
            QuizGenerationRequest request,
            RandomGenerator random
    ) {
        List<DocumentType> distractors = new ArrayList<>(KNOWN_DOCUMENT_TYPES);
        distractors.remove(correct.documentType());
        shuffle(distractors, random);
        requireDistractors(
                distractors.size(), request.optionCount() - 1,
                QuizQuestionType.DOCUMENT_TYPE, request, KNOWN_DOCUMENT_TYPES.size()
        );

        List<QuizContent> options = new ArrayList<>(request.optionCount());
        options.add(new QuizContent.DocumentTypeValue(correct.documentType()));
        for (int index = 0; index < request.optionCount() - 1; index++) {
            options.add(new QuizContent.DocumentTypeValue(distractors.get(index)));
        }
        shuffle(options, random);
        return new QuizQuestion(
                questionId,
                QuizQuestionType.DOCUMENT_TYPE,
                correct.documentContent(),
                options,
                correct.documentType().name()
        );
    }

    private QuizQuestion findOrdinaryPassport(
            long questionId,
            QuizCandidate correct,
            List<QuizCandidate> candidates,
            QuizGenerationRequest request,
            RandomGenerator random
    ) {
        List<QuizCandidate> distractors = candidates.stream()
                .filter(candidate -> candidate.documentType() != DocumentType.ORDINARY_PASSPORT)
                .filter(candidate -> !candidate.imageSha256().equals(correct.imageSha256()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        shuffle(distractors, random);
        requireDistractors(
                distractors.size(), request.optionCount() - 1,
                QuizQuestionType.FIND_ORDINARY_PASSPORT, request, candidates.size()
        );

        List<QuizContent> options = new ArrayList<>(request.optionCount());
        options.add(correct.documentContent());
        for (int index = 0; index < request.optionCount() - 1; index++) {
            options.add(distractors.get(index).documentContent());
        }
        shuffle(options, random);
        return new QuizQuestion(
                questionId,
                QuizQuestionType.FIND_ORDINARY_PASSPORT,
                new QuizContent.DocumentTypeValue(DocumentType.ORDINARY_PASSPORT),
                options,
                correct.documentId()
        );
    }

    private static List<QuizCandidate> distinctEntities(
            List<QuizCandidate> candidates,
            int limit
    ) {
        List<QuizCandidate> selected = new ArrayList<>(limit);
        Set<String> entityIds = new LinkedHashSet<>();
        for (QuizCandidate candidate : candidates) {
            if (entityIds.add(candidate.entityId())) {
                selected.add(candidate);
                if (selected.size() == limit) {
                    break;
                }
            }
        }
        return selected;
    }

    private static String questionKey(Blueprint blueprint) {
        String subjectKey = blueprint.type() == QuizQuestionType.COUNTRY_TO_PASSPORT
                ? blueprint.candidate().entityId()
                : blueprint.candidate().canonicalDocumentId();
        return blueprint.type().name() + ':' + subjectKey;
    }

    private static void validateRequest(QuizGenerationRequest request) {
        if (request.questionCount() <= 0) {
            throw failure(
                    QuizGenerationFailureReason.INVALID_REQUEST,
                    "questionCount must be positive",
                    request,
                    0,
                    null
            );
        }
        if (request.optionCount() < 2) {
            throw failure(
                    QuizGenerationFailureReason.INVALID_REQUEST,
                    "optionCount must be at least two",
                    request,
                    0,
                    null
            );
        }
        if (request.allowedQuestionTypes().isEmpty()) {
            throw failure(
                    QuizGenerationFailureReason.INVALID_REQUEST,
                    "allowedQuestionTypes must not be empty",
                    request,
                    0,
                    null
            );
        }
        if (request.allowedQuestionTypes().contains(QuizQuestionType.DOCUMENT_TYPE)
                && request.optionCount() > KNOWN_DOCUMENT_TYPES.size()) {
            throw failure(
                    QuizGenerationFailureReason.INVALID_REQUEST,
                    "DOCUMENT_TYPE supports at most four answer options",
                    request,
                    0,
                    QuizQuestionType.DOCUMENT_TYPE
            );
        }
        if (containsBlank(request.continentIds()) || containsBlank(request.entityIds())) {
            throw failure(
                    QuizGenerationFailureReason.INVALID_REQUEST,
                    "scope IDs must not be blank",
                    request,
                    0,
                    null
            );
        }
        if (request.documentTypes().contains(DocumentType.UNKNOWN)) {
            throw failure(
                    QuizGenerationFailureReason.INVALID_REQUEST,
                    "UNKNOWN cannot be a document-type scope",
                    request,
                    0,
                    null
            );
        }
    }

    private static boolean containsBlank(Collection<String> values) {
        return values.stream().anyMatch(String::isBlank);
    }

    private static void requireDistractors(
            int available,
            int required,
            QuizQuestionType type,
            QuizGenerationRequest request,
            int availableCandidates
    ) {
        if (available < required) {
            throw failure(
                    QuizGenerationFailureReason.INSUFFICIENT_DISTRACTORS,
                    type + " requires " + required + " distractors but only " + available
                            + " are available",
                    request,
                    availableCandidates,
                    type
            );
        }
    }

    private static QuizGenerationException failure(
            QuizGenerationFailureReason reason,
            String detail,
            QuizGenerationRequest request,
            int availableCandidates,
            QuizQuestionType type
    ) {
        return new QuizGenerationException(
                reason,
                detail,
                request.questionCount(),
                availableCandidates,
                type
        );
    }

    private static <T> void shuffle(List<T> values, RandomGenerator random) {
        for (int index = values.size() - 1; index > 0; index--) {
            Collections.swap(values, index, random.nextInt(index + 1));
        }
    }

    private record Blueprint(QuizQuestionType type, QuizCandidate candidate) {
    }
}
