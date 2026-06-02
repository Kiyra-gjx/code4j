package code4j.permissions.api;

import code4j.permissions.model.PermissionDecision;
import code4j.permissions.model.PermissionPromptResult;
import code4j.permissions.model.PermissionRequest;

import java.util.Objects;

@FunctionalInterface
public interface PermissionPromptHandler {
    PermissionPromptResult prompt(PermissionRequest request);

    static PermissionPromptHandler unavailable() {
        return request -> PermissionPromptResult.deny(PermissionDecision.DENY_WITH_FEEDBACK,
                "No prompt handler for " + Objects.requireNonNull(request, "request").kind());
    }
}
