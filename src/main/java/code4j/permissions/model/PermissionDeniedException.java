package code4j.permissions.model;

import java.util.Objects;
import java.util.Optional;

public class PermissionDeniedException extends RuntimeException {
    private final PermissionRequest request;
    private final Optional<String> feedback;

    public PermissionDeniedException(PermissionRequest request, Optional<String> choiceKey, Optional<String> feedback) {
        super(feedback.filter(v -> !v.isBlank()).map(v -> "Permission denied: " + v).orElse("Permission denied"));
        this.request = Objects.requireNonNull(request, "request");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    public PermissionRequest request() { return request; }
    public Optional<String> feedback() { return feedback; }
}
