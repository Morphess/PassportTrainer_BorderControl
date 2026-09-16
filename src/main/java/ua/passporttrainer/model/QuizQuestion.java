package ua.passporttrainer.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A validated multiple-choice question using stable catalogue identities. */
public record QuizQuestion(
        long id,
        QuizQuestionType type,
        QuizContent subject,
        List<QuizContent> options,
        String correctAnswerId
) {

    public QuizQuestion {
        type = Objects.requireNonNull(type, "type");
        subject = Objects.requireNonNull(subject, "subject");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        correctAnswerId = requireText(correctAnswerId, "correctAnswerId");

        if (options.size() < 2) {
            throw new IllegalArgumentException("options must contain at least two values");
        }
        Set<String> optionIds = new HashSet<>();
        for (QuizContent option : options) {
            if (!optionIds.add(option.id())) {
                throw new IllegalArgumentException("option IDs must be unique: " + option.id());
            }
        }
        if (!optionIds.contains(correctAnswerId)) {
            throw new IllegalArgumentException("correctAnswerId must identify one option");
        }
        validateQuestionShape(type, subject, options, correctAnswerId);
    }

    public boolean hasOption(String answerId) {
        Objects.requireNonNull(answerId, "answerId");
        return options.stream().anyMatch(option -> option.id().equals(answerId));
    }

    private static void validateQuestionShape(
            QuizQuestionType type,
            QuizContent subject,
            List<QuizContent> options,
            String correctAnswerId
    ) {
        switch (type) {
            case PASSPORT_TO_COUNTRY -> validatePassportToCountry(subject, options, correctAnswerId);
            case COUNTRY_TO_PASSPORT -> validateCountryToPassport(subject, options, correctAnswerId);
            case DOCUMENT_TYPE -> validateDocumentType(subject, options, correctAnswerId);
            case FIND_ORDINARY_PASSPORT ->
                    validateFindOrdinaryPassport(subject, options, correctAnswerId);
        }
    }

    private static void validatePassportToCountry(
            QuizContent subject,
            List<QuizContent> options,
            String correctAnswerId
    ) {
        QuizContent.Document document = requireSubject(
                subject, QuizContent.Document.class, "PASSPORT_TO_COUNTRY"
        );
        requireOptions(options, QuizContent.Country.class, "PASSPORT_TO_COUNTRY");
        if (!correctAnswerId.equals(document.entityId())) {
            throw new IllegalArgumentException(
                    "PASSPORT_TO_COUNTRY correct answer must identify the document entity"
            );
        }
    }

    private static void validateCountryToPassport(
            QuizContent subject,
            List<QuizContent> options,
            String correctAnswerId
    ) {
        QuizContent.Country country = requireSubject(
                subject, QuizContent.Country.class, "COUNTRY_TO_PASSPORT"
        );
        requireOptions(options, QuizContent.Document.class, "COUNTRY_TO_PASSPORT");
        List<QuizContent.Document> matchingDocuments = options.stream()
                .map(QuizContent.Document.class::cast)
                .filter(document -> document.entityId().equals(country.id()))
                .toList();
        if (matchingDocuments.size() != 1
                || !matchingDocuments.getFirst().id().equals(correctAnswerId)) {
            throw new IllegalArgumentException(
                    "COUNTRY_TO_PASSPORT must contain exactly one document from the subject entity"
            );
        }
    }

    private static void validateDocumentType(
            QuizContent subject,
            List<QuizContent> options,
            String correctAnswerId
    ) {
        QuizContent.Document document = requireSubject(
                subject, QuizContent.Document.class, "DOCUMENT_TYPE"
        );
        requireOptions(options, QuizContent.DocumentTypeValue.class, "DOCUMENT_TYPE");
        if (!correctAnswerId.equals(document.documentType().name())) {
            throw new IllegalArgumentException(
                    "DOCUMENT_TYPE correct answer must identify the document type"
            );
        }
    }

    private static void validateFindOrdinaryPassport(
            QuizContent subject,
            List<QuizContent> options,
            String correctAnswerId
    ) {
        QuizContent.DocumentTypeValue target = requireSubject(
                subject, QuizContent.DocumentTypeValue.class, "FIND_ORDINARY_PASSPORT"
        );
        if (target.documentType() != DocumentType.ORDINARY_PASSPORT) {
            throw new IllegalArgumentException(
                    "FIND_ORDINARY_PASSPORT subject must be ORDINARY_PASSPORT"
            );
        }
        requireOptions(options, QuizContent.Document.class, "FIND_ORDINARY_PASSPORT");
        List<QuizContent.Document> ordinaryDocuments = options.stream()
                .map(QuizContent.Document.class::cast)
                .filter(document -> document.documentType() == DocumentType.ORDINARY_PASSPORT)
                .toList();
        if (ordinaryDocuments.size() != 1
                || !ordinaryDocuments.getFirst().id().equals(correctAnswerId)) {
            throw new IllegalArgumentException(
                    "FIND_ORDINARY_PASSPORT must contain exactly one ordinary passport"
            );
        }
    }

    private static <T extends QuizContent> T requireSubject(
            QuizContent subject,
            Class<T> expectedType,
            String questionType
    ) {
        if (!expectedType.isInstance(subject)) {
            throw new IllegalArgumentException(
                    questionType + " has an incompatible subject"
            );
        }
        return expectedType.cast(subject);
    }

    private static void requireOptions(
            List<QuizContent> options,
            Class<? extends QuizContent> expectedType,
            String questionType
    ) {
        if (options.stream().anyMatch(option -> !expectedType.isInstance(option))) {
            throw new IllegalArgumentException(
                    questionType + " has an incompatible answer option"
            );
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
