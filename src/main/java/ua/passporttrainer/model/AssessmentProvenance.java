package ua.passporttrainer.model;

import java.util.Objects;

/** Stable identity of a future authored assessment definition and its immutable version. */
public record AssessmentProvenance(String assessmentId, String versionId) {

    public AssessmentProvenance {
        assessmentId = requireText(assessmentId, "assessmentId");
        versionId = requireText(versionId, "versionId");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
