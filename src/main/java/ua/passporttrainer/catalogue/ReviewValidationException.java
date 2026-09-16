package ua.passporttrainer.catalogue;

import java.util.Objects;

final class ReviewValidationException extends RuntimeException {

    private final ReviewFailureReason reason;
    private final String targetId;

    ReviewValidationException(ReviewFailureReason reason, String targetId, String message) {
        this(reason, targetId, message, null);
    }

    ReviewValidationException(ReviewFailureReason reason, String targetId, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.targetId = targetId == null ? "" : targetId;
    }

    ReviewFailureReason reason() {
        return reason;
    }

    String targetId() {
        return targetId;
    }
}
