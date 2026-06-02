package code4j.tui;

import code4j.app.ApplicationServices;
import code4j.core.event.AgentEvent;
import code4j.core.message.*;
import code4j.core.turn.AgentTurnResult;
import code4j.core.turn.AgentTurnStopReason;
import code4j.permissions.model.*;
import code4j.session.plan.PersistenceAction;
import code4j.session.plan.TurnPersistencePlan;
import code4j.tui.input.LineTuiInput;
import code4j.tui.input.TuiInput;
import code4j.tui.input.TuiInputEvent;
import code4j.tui.render.*;
import code4j.tui.terminal.TerminalScreen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class RendererTuiShell {
    private final ApplicationServices services;
    private final TuiInput input;
    private final TerminalScreen screen;
    private final TuiRenderer renderer;
    private final int maxSteps;
    private final Object lock = new Object();
    private RenderState state;
    private Thread activeTurn;
    private PendingPermission pendingPermission;

    public RendererTuiShell(ApplicationServices services, LineInput input, TerminalScreen screen, int maxSteps,
                            RendererTuiBridge bridge) {
        this(services, new LineTuiInput(input), screen, maxSteps, bridge);
    }

    public RendererTuiShell(ApplicationServices services, TuiInput input, TerminalScreen screen, int maxSteps,
                            RendererTuiBridge bridge) {
        this.services = Objects.requireNonNull(services, "services");
        this.input = Objects.requireNonNull(input, "input");
        this.screen = Objects.requireNonNull(screen, "screen");
        this.renderer = new TuiRenderer();
        if (maxSteps < 1) throw new IllegalArgumentException("maxSteps must be positive");
        this.maxSteps = maxSteps;
        if (bridge != null) bridge.attach(this);
        this.state = RenderState.empty();
        redraw();
    }

    public void runLoop() {
        try {
            while (true) { if (!runEvent(input.readEvent())) return; }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    void onAgentEvent(AgentEvent event) {
        synchronized (lock) {
            switch (event) {
                case AgentEvent.AssistantMessageEvent e -> {
                    if (e.message() instanceof AssistantMessage am) appendTranscript(TranscriptBlock.assistant(am.content()));
                    else if (e.message() instanceof AssistantProgressMessage pm) appendTranscript(TranscriptBlock.progress(pm.content()));
                }
                case AgentEvent.ToolStartedEvent e -> {
                    String s = ToolInputSummarizer.summarize(e.toolName(), e.input());
                    appendTranscript(TranscriptBlock.toolStarted(e.toolUseId(), e.toolName(), s));
                    state = state.withStatus(StatusState.of("Running " + e.toolName() + "..."))
                            .withInput(state.input().withMode(InputState.Mode.BUSY));
                }
                case AgentEvent.ToolFinishedEvent e -> {
                    appendTranscript(TranscriptBlock.toolResult(e.toolUseId(), e.toolName(), e.error(), ""));
                    if (e.awaitUser()) {
                        state = state.withStatus(StatusState.of("Waiting..."))
                                .withInput(state.input().withMode(InputState.Mode.AWAITING_ASK_USER));
                    } else {
                        state = state.withStatus(StatusState.thinking())
                                .withInput(state.input().withMode(InputState.Mode.BUSY));
                    }
                }
                case AgentEvent.AwaitUserEvent e -> {
                    appendTranscript(TranscriptBlock.askUser(e.toolUseId(), e.question()));
                    state = state.withStatus(StatusState.of("Waiting..."))
                            .withInput(state.input().withMode(InputState.Mode.AWAITING_ASK_USER));
                }
                case AgentEvent.ContextStatsEvent e -> state = state.withContextBadge("ctx " + Math.round(e.stats().utilization() * 100) + "%");
                case AgentEvent.TurnCancelledEvent e -> appendTranscript(TranscriptBlock.diagnostic("Cancelled: " + e.cancellation().reason()));
                case AgentEvent.AutoCompactEvent e -> {
                    if (e.type() == code4j.context.compact.AutoCompactEventType.COMPLETED)
                        appendTranscript(TranscriptBlock.compact("Compact done"));
                }
                default -> {}
            }
            redraw();
        }
    }

    PermissionPromptResult requestPermission(PermissionRequest request) {
        CompletableFuture<PermissionPromptResult> future = new CompletableFuture<>();
        synchronized (lock) {
            pendingPermission = new PendingPermission(request, future);
            appendTranscript(TranscriptBlock.permission(request.requestId(), permissionText(request)));
            state = state.withStatus(StatusState.of("Permission required"))
                    .withInput(InputState.empty().withMode(InputState.Mode.PENDING_PERMISSION));
            redraw();
        }
        try { return future.get(365, TimeUnit.DAYS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return PermissionPromptResult.deny(PermissionDecision.DENY_WITH_FEEDBACK, "Interrupted"); }
        catch (Exception e) { return PermissionPromptResult.deny(PermissionDecision.DENY_WITH_FEEDBACK, e.getMessage()); }
    }

    private boolean runEvent(TuiInputEvent event) {
        return switch (event.kind()) {
            case EOF -> false;
            case PAGE_UP, SCROLL_UP -> { scroll(-1); yield true; }
            case PAGE_DOWN, SCROLL_DOWN -> { scroll(1); yield true; }
            case CURSOR_LEFT -> { moveCursor(-1); yield true; }
            case CURSOR_RIGHT -> { moveCursor(1); yield true; }
            case CHARACTER -> { typeChar(event.character().orElseThrow()); yield true; }
            case BACKSPACE -> { backspace(); yield true; }
            case SUBMIT -> runLine(submittedText(event));
        };
    }

    private String submittedText(TuiInputEvent event) {
        if (event.text().isPresent()) return event.text().orElseThrow();
        synchronized (lock) { return state.input().text(); }
    }

    private boolean runLine(String line) {
        if (line == null) return false;
        PendingPermission pp;
        synchronized (lock) { pp = pendingPermission; }
        if (pp != null) {
            PermissionPromptResult result = handlePermissionInput(line);
            if (result != null) pp.future().complete(result);
            return true;
        }
        String t = line.trim();
        if (t.isBlank()) return true;
        if ("/exit".equalsIgnoreCase(t) || "/quit".equalsIgnoreCase(t)) return false;
        if ("/compact".equalsIgnoreCase(t)) { runCompact(); return true; }
        synchronized (lock) {
            if (activeTurn != null && activeTurn.isAlive()) { appendTranscript(TranscriptBlock.diagnostic("Busy...")); redraw(); return true; }
        }
        boolean answerMode;
        synchronized (lock) { answerMode = state.input().mode() == InputState.Mode.AWAITING_ASK_USER; }
        startTurn(line, answerMode);
        return true;
    }

    private void startTurn(String line, boolean answerMode) {
        synchronized (lock) {
            if (answerMode) { appendTranscript(TranscriptBlock.user("answer: " + line)); }
            else { appendTranscript(TranscriptBlock.user(line)); }
            state = state.withStatus(StatusState.thinking()).withInput(InputState.empty().withMode(InputState.Mode.BUSY));
            redraw();
        }
        activeTurn = Thread.currentThread();
        try {
            UserMessage userMsg = new UserMessage(line);
            List<ChatMessage> history = new ArrayList<>(services.sessionMessages());
            history.add(userMsg);
            AgentTurnResult result = services.runTurn(services.turnRequest(history, maxSteps));
            synchronized (lock) {
                appendTranscript(TranscriptBlock.diagnostic("turn: " + result.stopReason()));
                state = state.withInput(InputState.empty());
                if (result.stopReason() == AgentTurnStopReason.AWAIT_USER)
                    state = state.withInput(state.input().withMode(InputState.Mode.AWAITING_ASK_USER));
                redraw();
            }
        } finally { synchronized (lock) { activeTurn = null; } }
    }

    private void runCompact() {
        var result = services.manualCompact();
        synchronized (lock) { appendTranscript(TranscriptBlock.compact("Compact: " + result.status())); redraw(); }
    }

    private PermissionPromptResult handlePermissionInput(String line) {
        PendingPermission pp;
        synchronized (lock) { pp = pendingPermission; }
        if (pp == null) return null;
        String t = line.trim();
        try { int idx = Integer.parseInt(t); if (idx >= 1 && idx <= pp.request().choices().size()) { var c = pp.request().choices().get(idx - 1); return resolve(c); } }
        catch (NumberFormatException ignored) {}
        for (PermissionChoice c : pp.request().choices()) {
            if (c.key().equalsIgnoreCase(t) || c.label().equalsIgnoreCase(t)) return resolve(c);
        }
        return null;
    }

    private PermissionPromptResult resolve(PermissionChoice c) {
        synchronized (lock) { pendingPermission = null; }
        if (c.requiresFeedback()) {
            synchronized (lock) { state = state.withInput(InputState.empty().withMode(InputState.Mode.PERMISSION_FEEDBACK)); redraw(); }
            return null;
        }
        boolean allow = c.decision() == PermissionDecision.ALLOW_ONCE || c.decision() == PermissionDecision.ALLOW_TURN || c.decision() == PermissionDecision.ALLOW_ALWAYS;
        synchronized (lock) { state = state.withInput(InputState.empty()).withStatus(StatusState.empty()); redraw(); }
        return allow ? PermissionPromptResult.allow(c.decision()) : PermissionPromptResult.deny(c.decision(), null);
    }

    private void appendTranscript(TranscriptBlock block) { state = state.appendTranscript(block); }
    private void redraw() { screen.redraw(renderer.render(state, screen.size())); }
    private void scroll(int dir) { synchronized (lock) { state = state.withScrollOffset(Math.max(0, state.scrollOffset() + dir * 5)); redraw(); } }
    private void moveCursor(int dir) { synchronized (lock) { InputState is = state.input(); state = state.withInput(new InputState(is.mode(), is.text(), Math.max(0, Math.min(is.text().length(), is.cursor() + dir)))); redraw(); } }
    private void typeChar(char c) { synchronized (lock) { InputState is = state.input(); String t = is.text(); int pos = is.cursor(); state = state.withInput(new InputState(is.mode(), t.substring(0, pos) + c + t.substring(pos), pos + 1)); redraw(); } }
    private void backspace() { synchronized (lock) { InputState is = state.input(); int pos = is.cursor(); if (pos == 0) return; String t = is.text(); state = state.withInput(new InputState(is.mode(), t.substring(0, pos - 1) + t.substring(pos), pos - 1)); redraw(); } }

    private static String permissionText(PermissionRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.details().title()).append("\n").append(r.details().body()).append("\n");
        for (String f : r.details().facts()) sb.append("  ").append(f).append("\n");
        for (int i = 0; i < r.choices().size(); i++) {
            var c = r.choices().get(i);
            sb.append("  ").append(i + 1).append(") ").append(c.label()).append(" [").append(c.key()).append("]\n");
        }
        return sb.toString();
    }

    private record PendingPermission(PermissionRequest request, CompletableFuture<PermissionPromptResult> future) {}
}
