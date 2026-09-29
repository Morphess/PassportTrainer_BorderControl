package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import ua.passporttrainer.model.AuthoredQuestionType;
import ua.passporttrainer.model.Question;
import ua.passporttrainer.model.QuestionLifecycleState;
import ua.passporttrainer.model.QuestionOption;
import ua.passporttrainer.model.QuestionVersion;
import ua.passporttrainer.repository.QuestionRepository;
import ua.passporttrainer.repository.QuestionVersionRepository;
import ua.passporttrainer.security.AuthenticatedUser;

/** Authorization-aware application boundary for authored question foundations. */
public final class AdminQuestionService {

    private final AuthorizationService authorizationService;
    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final Clock clock;
    private final Supplier<String> idSupplier;

    public AdminQuestionService(
            AuthorizationService authorizationService,
            QuestionRepository questionRepository,
            QuestionVersionRepository versionRepository,
            Clock clock,
            Supplier<String> idSupplier
    ) {
        this.authorizationService = Objects.requireNonNull(
                authorizationService, "authorizationService"
        );
        this.questionRepository = Objects.requireNonNull(
                questionRepository, "questionRepository"
        );
        this.versionRepository = Objects.requireNonNull(
                versionRepository, "versionRepository"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
    }

    public Question createDraftQuestion() {
        AuthenticatedUser administrator = authorizationService.requireAdmin();
        Instant now = clock.instant();
        return questionRepository.create(new Question(
                nextId("question"),
                QuestionLifecycleState.DRAFT,
                administrator.id(),
                now,
                now
        ));
    }

    public Optional<Question> findQuestion(String questionId) {
        authorizationService.requireAdmin();
        return questionRepository.findById(questionId);
    }

    public List<Question> listQuestions() {
        authorizationService.requireAdmin();
        return questionRepository.findAll();
    }

    public Question archiveQuestion(String questionId) {
        authorizationService.requireAdmin();
        Question question = requireQuestion(questionId);
        if (question.lifecycleState() == QuestionLifecycleState.ARCHIVED) {
            return question;
        }
        return questionRepository.update(new Question(
                question.questionId(),
                QuestionLifecycleState.ARCHIVED,
                question.createdBy(),
                question.createdAt(),
                timestampNotBefore(question.updatedAt())
        ));
    }

    public QuestionVersion createQuestionVersion(
            String questionId,
            int versionNumber,
            AuthoredQuestionType questionType,
            String prompt,
            Optional<String> instructions,
            Optional<String> explanation,
            Optional<String> difficulty,
            List<QuestionOption> options
    ) {
        AuthenticatedUser administrator = authorizationService.requireAdmin();
        Question question = requireQuestion(questionId);
        if (question.lifecycleState() == QuestionLifecycleState.ARCHIVED) {
            throw new IllegalStateException("Cannot version an archived question: " + questionId);
        }
        return versionRepository.append(new QuestionVersion(
                nextId("question-version"),
                question.questionId(),
                versionNumber,
                questionType,
                prompt,
                instructions,
                explanation,
                difficulty,
                options,
                administrator.id(),
                clock.instant()
        ));
    }

    public Optional<QuestionVersion> findQuestionVersion(String versionId) {
        authorizationService.requireAdmin();
        return versionRepository.findById(versionId);
    }

    public List<QuestionVersion> listQuestionVersions(String questionId) {
        authorizationService.requireAdmin();
        return versionRepository.findByQuestionId(questionId);
    }

    private Question requireQuestion(String questionId) {
        return questionRepository.findById(questionId).orElseThrow(() ->
                new IllegalArgumentException("Unknown authored question ID: " + questionId)
        );
    }

    private Instant timestampNotBefore(Instant previous) {
        Instant now = clock.instant();
        return now.isBefore(previous) ? previous : now;
    }

    private String nextId(String namespace) {
        String token = Objects.requireNonNull(idSupplier.get(), "generated ID");
        if (token.isBlank()) {
            throw new IllegalStateException("Generated ID must not be blank");
        }
        return namespace + ":" + token;
    }
}
