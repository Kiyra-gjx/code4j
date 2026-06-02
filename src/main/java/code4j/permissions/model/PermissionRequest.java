package code4j.permissions.model;

import java.util.*;

public record PermissionRequest(String requestId, PermissionRequestKind kind, PermissionResource resource,
                                String reason, PermissionRequestDetails details, List<PermissionChoice> choices,
                                boolean feedbackAllowed, PermissionScope scope, PermissionContext context) {
    public PermissionRequest {
        if (Objects.requireNonNull(requestId, "requestId").isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        kind = Objects.requireNonNull(kind, "kind");
        resource = Objects.requireNonNull(resource, "resource");
        if (Objects.requireNonNull(reason, "reason").isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        details = Objects.requireNonNull(details, "details");
        choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("must include at least one choice");
        }
        Set<String> keys = new HashSet<>();
        for (PermissionChoice c : choices) {
            if (!keys.add(c.key())) {
                throw new IllegalArgumentException("choice keys must be unique");
            }
            if (c.requiresFeedback() && !feedbackAllowed) {
                throw new IllegalArgumentException("feedback choices require feedbackAllowed");
            }
        }
        scope = Objects.requireNonNull(scope, "scope");
        context = Objects.requireNonNull(context, "context");
    }

    public static List<PermissionChoice> defaultChoices() {
        return List.of(
                PermissionChoice.allowOnce("allow_once", "Allow once"),
                PermissionChoice.allowTurn("allow_turn", "Allow turn"),
                PermissionChoice.allowAlways("allow_always", "Allow always"),
                PermissionChoice.denyOnce("deny_once", "Deny once"),
                PermissionChoice.denyAlways("deny_always", "Deny always"),
                PermissionChoice.denyWithFeedback("deny_feedback", "Deny with feedback"));
    }
}
