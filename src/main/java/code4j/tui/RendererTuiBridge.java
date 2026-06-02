package code4j.tui;

import code4j.core.event.AgentEvent;
import code4j.core.event.AgentEventSink;
import code4j.permissions.api.PermissionPromptHandler;
import code4j.permissions.model.PermissionDecision;
import code4j.permissions.model.PermissionPromptResult;
import code4j.permissions.model.PermissionRequest;

import java.util.Objects;

public final class RendererTuiBridge implements AgentEventSink, PermissionPromptHandler {
    private volatile RendererTuiShell shell;

    void attach(RendererTuiShell shell) { this.shell = Objects.requireNonNull(shell, "shell"); }

    @Override
    public void onEvent(AgentEvent event) { RendererTuiShell s = shell; if (s != null) s.onAgentEvent(event); }

    @Override
    public PermissionPromptResult prompt(PermissionRequest request) {
        RendererTuiShell s = shell;
        if (s == null) return PermissionPromptResult.deny(PermissionDecision.DENY_WITH_FEEDBACK, "Not attached");
        return s.requestPermission(request);
    }
}
