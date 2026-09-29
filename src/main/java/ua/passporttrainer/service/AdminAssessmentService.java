package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import ua.passporttrainer.model.AssessmentDefinition;
import ua.passporttrainer.model.AssessmentState;
import ua.passporttrainer.repository.AssessmentRepository;
import ua.passporttrainer.security.AuthenticatedUser;

/** Authorization-aware boundary for minimal authored assessment persistence. */
public final class AdminAssessmentService {

    private final AuthorizationService authorizationService;
    private final AssessmentRepository assessmentRepository;
    private final Clock clock;
    private final Supplier<String> idSupplier;

    public AdminAssessmentService(
            AuthorizationService authorizationService,
            AssessmentRepository assessmentRepository,
            Clock clock,
            Supplier<String> idSupplier
    ) {
        this.authorizationService = Objects.requireNonNull(
                authorizationService, "authorizationService"
        );
        this.assessmentRepository = Objects.requireNonNull(
                assessmentRepository, "assessmentRepository"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
    }

    public AssessmentDefinition createDraftAssessment(
            String title,
            Optional<String> description
    ) {
        AuthenticatedUser administrator = authorizationService.requireAdmin();
        Instant now = clock.instant();
        return assessmentRepository.create(new AssessmentDefinition(
                nextId(),
                AssessmentState.DRAFT,
                title,
                description,
                administrator.id(),
                now,
                now
        ));
    }

    public Optional<AssessmentDefinition> findAssessment(String assessmentId) {
        authorizationService.requireAdmin();
        return assessmentRepository.findById(assessmentId);
    }

    public List<AssessmentDefinition> listAssessments() {
        authorizationService.requireAdmin();
        return assessmentRepository.findAll();
    }

    public AssessmentDefinition archiveAssessment(String assessmentId) {
        authorizationService.requireAdmin();
        AssessmentDefinition assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown authored assessment ID: " + assessmentId
                ));
        if (assessment.state() == AssessmentState.ARCHIVED) {
            return assessment;
        }
        return assessmentRepository.update(new AssessmentDefinition(
                assessment.assessmentId(),
                AssessmentState.ARCHIVED,
                assessment.title(),
                assessment.description(),
                assessment.createdBy(),
                assessment.createdAt(),
                timestampNotBefore(assessment.updatedAt())
        ));
    }

    private Instant timestampNotBefore(Instant previous) {
        Instant now = clock.instant();
        return now.isBefore(previous) ? previous : now;
    }

    private String nextId() {
        String token = Objects.requireNonNull(idSupplier.get(), "generated ID");
        if (token.isBlank()) {
            throw new IllegalStateException("Generated ID must not be blank");
        }
        return "assessment:" + token;
    }
}
