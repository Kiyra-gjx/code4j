package code4j.permissions.model;

import java.util.Objects;
import java.util.Optional;

public record PermissionPromptResult(PermissionDecision decision, Optional<String> choiceKey,
                                     Optional<String> feedback) {
    public PermissionPromptResult {
        decision = Objects.requireNonNull(decision, "decision");
        choiceKey = Objects.requireNonNull(choiceKey, "choiceKey");
        feedback = Objects.requireNonNull(feedback, "feedback");
        if (decision == PermissionDecision.DENY_WITH_FEEDBACK && feedback.filter(v -> !v.isBlank()).isEmpty()) {
            throw new IllegalArgumentException("deny with feedback requires feedback");
        }
        if (isAllow(decision) && feedback.isPresent()) {
            throw new IllegalArgumentException("allow cannot carry feedback");
        }
    }

    public static PermissionPromptResult allow(PermissionDecision decision) {
        return new PermissionPromptResult(decision, Optional.empty(), Optional.empty());
    }

    public static PermissionPromptResult deny(PermissionDecision decision, String feedback) {
        return new PermissionPromptResult(decision, Optional.empty(), Optional.ofNullable(feedback));
    }

    public boolean allowed() { return isAllow(decision); }

    private static boolean isAllow(PermissionDecision d) {
        return d == PermissionDecision.ALLOW_ONCE || d == PermissionDecision.ALLOW_TURN || d == PermissionDecision.ALLOW_ALWAYS;
    }
}
