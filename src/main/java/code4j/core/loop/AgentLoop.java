package code4j.core.loop;

import code4j.context.compact.*;
import code4j.context.manager.ContextManager;
import code4j.context.stats.ContextStats;
import code4j.context.stats.ContextStatsCalculator;
import code4j.context.stats.ModelContextWindow;
import code4j.context.accounting.TokenAccountingService;
import code4j.core.event.AgentEvent;
import code4j.core.event.AgentEventSink;
import code4j.core.event.ToolResultsBudgetedEvent;
import code4j.core.message.*;
import code4j.core.step.*;
import code4j.core.turn.*;
import code4j.model.ModelRequestException;
import code4j.model.UsageStaleness;
import code4j.session.plan.PersistenceAction;
import code4j.session.plan.TurnPersistencePlan;
import code4j.tools.api.ToolCall;
import code4j.tools.api.ToolContext;
import code4j.tools.api.ToolExecutor;
import code4j.tools.result.ToolResult;
import code4j.tools.result.ToolResultBudgetResult;
import code4j.tools.result.ToolResultReplacementResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The core agent loop. Takes an {@link AgentTurnRequest}, iterates:
 * model → tools → model → tools → ..., and produces an {@link AgentTurnResult}.
 */
public final class AgentLoop {
    private static final String EMPTY_RESPONSE_MESSAGE = "The model returned an empty response after retries.";
    private static final String PROGRESS_CONTINUATION_PROMPT = "Continue immediately with concrete tool calls or final answer.";
    private static final String EMPTY_RESPONSE_CONTINUATION_PROMPT = "Your last response was empty. Continue immediately.";
    private static final String EMPTY_RESPONSE_AFTER_TOOL_ERROR_CONTINUATION_PROMPT = "Your last response was empty after tool errors. Adapt and continue.";
    private static final int MODEL_REQUEST_ATTEMPTS = 3;

    private final ModelAdapter modelAdapter;
    private final AgentEventSink eventSink;
    private final ToolExecutor toolExecutor;
    private final ContextManager contextManager;
    private final ContextStatsCalculator contextStatsCalculator;
    private final AutoCompactController autoCompactController;
    private final int maxEmptyResponseRetries;

    public AgentLoop(ModelAdapter modelAdapter, AgentEventSink eventSink) {
        this(modelAdapter, eventSink, ToolExecutor.unsupported(), ContextManager.noOp(), 2);
    }

    public AgentLoop(ModelAdapter modelAdapter, AgentEventSink eventSink, ToolExecutor toolExecutor) {
        this(modelAdapter, eventSink, toolExecutor, ContextManager.noOp(), 2);
    }

    public AgentLoop(ModelAdapter modelAdapter, AgentEventSink eventSink, ToolExecutor toolExecutor,
                     ContextManager contextManager) {
        this(modelAdapter, eventSink, toolExecutor, contextManager, 2);
    }

    public AgentLoop(ModelAdapter modelAdapter, AgentEventSink eventSink, ToolExecutor toolExecutor,
                     ContextManager contextManager, int maxEmptyResponseRetries) {
        this(modelAdapter, eventSink, toolExecutor, contextManager,
                new ContextStatsCalculator(new TokenAccountingService(), new ModelContextWindow(128_000, 8_000)),
                AutoCompactController.disabled(), maxEmptyResponseRetries);
    }

    public AgentLoop(ModelAdapter modelAdapter, AgentEventSink eventSink, ToolExecutor toolExecutor,
                     ContextManager contextManager, ContextStatsCalculator contextStatsCalculator,
                     AutoCompactController autoCompactController, int maxEmptyResponseRetries) {
        this.modelAdapter = Objects.requireNonNull(modelAdapter, "modelAdapter");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor");
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager");
        this.contextStatsCalculator = Objects.requireNonNull(contextStatsCalculator, "contextStatsCalculator");
        this.autoCompactController = Objects.requireNonNull(autoCompactController, "autoCompactController");
        if (maxEmptyResponseRetries < 0) throw new IllegalArgumentException("maxEmptyResponseRetries must be non-negative");
        this.maxEmptyResponseRetries = maxEmptyResponseRetries;
    }

    public AgentTurnResult runTurn(AgentTurnRequest request) {
        Objects.requireNonNull(request, "request");
        List<ChatMessage> messages = new ArrayList<>(request.messages());
        List<PersistenceAction> actions = new ArrayList<>();
        int emptyResponseCount = 0;
        boolean sawToolResultThisTurn = false;
        int toolErrorCount = 0;

        try {
            request.cancellationToken().throwIfCancellationRequested(CancellationPhase.BEFORE_TURN);
            for (int stepIndex = 0; stepIndex < request.maxSteps(); stepIndex++) {
                ContextStats preCompactStats = contextStatsCalculator.calculate(List.copyOf(messages));
                messages = new ArrayList<>(contextManager.microcompact(List.copyOf(messages), preCompactStats));
                ContextStats stats = contextStatsCalculator.calculate(List.copyOf(messages));
                AutoCompactResult acr = autoCompactPreflight(request.turnId(), messages, actions, stats);
                if (acr.status() == CompactStatus.COMPACTED) {
                    messages = new ArrayList<>(acr.messages());
                    stats = contextStatsCalculator.calculate(List.copyOf(messages));
                }
                publishEvent(new AgentEvent.ContextStatsEvent(request.turnId(), Instant.now(), stats));
                request.cancellationToken().throwIfCancellationRequested(CancellationPhase.MODEL_REQUEST);

                AgentStep step;
                try {
                    step = nextWithRetries(List.copyOf(messages), request.cancellationToken());
                    request.cancellationToken().throwIfCancellationRequested(CancellationPhase.MODEL_REQUEST);
                } catch (ModelRequestException e) {
                    return modelErrorResult(messages, actions, e);
                } catch (RuntimeException e) {
                    return modelErrorResult(messages, actions,
                            e.getMessage() == null || e.getMessage().isBlank() ? "Model adapter failed" : e.getMessage(),
                            e.getClass().getName());
                }

                if (step == null) {
                    return modelErrorResult(messages, actions, "Model adapter returned null AgentStep", NullPointerException.class.getName());
                }

                if (step instanceof ToolCallsStep tcs) {
                    appendToolCallsStepProjection(request.turnId(), messages, actions, tcs);
                    request.cancellationToken().throwIfCancellationRequested(CancellationPhase.AFTER_TURN);
                    List<ToolResultMessage> toolResults = new ArrayList<>();
                    for (int ci = 0; ci < tcs.calls().size(); ci++) {
                        ToolCall call = tcs.calls().get(ci);
                        appendToolCallMessage(request.turnId(), messages, actions, call,
                                ci == tcs.calls().size() - 1 ? tcs.usage() : Optional.empty());
                        publishEvent(new AgentEvent.ToolStartedEvent(request.turnId(), Instant.now(), call.id(), call.toolName(), call.input()));
                        request.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
                    }
                    for (ToolCall call : tcs.calls()) {
                        request.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
                        ToolResult result;
                        try {
                            result = toolExecutor.execute(call, createToolContext(request, call.id()));
                        } catch (RuntimeException e) {
                            result = ToolResult.error(e.getMessage() == null || e.getMessage().isBlank() ? "Tool execution failed" : e.getMessage());
                        }
                        if (result == null) result = ToolResult.error("Tool executor returned null");
                        sawToolResultThisTurn = true;
                        if (result.error()) toolErrorCount++;
                        ToolResultMessage trm = appendToolResultMessage(request.turnId(), messages, actions, call, result);
                        toolResults.add(trm);
                        request.cancellationToken().throwIfCancellationRequested(CancellationPhase.AFTER_TURN);
                        if (result.awaitUser()) {
                            applyToolResultBudget(request.turnId(), messages, actions, toolResults);
                            publishEvent(new AgentEvent.AwaitUserEvent(request.turnId(), Instant.now(), call.id(),
                                    awaitUserQuestion(call.id(), toolResults)));
                            return AgentTurnResult.awaitUser(List.copyOf(messages), new TurnPersistencePlan(actions));
                        }
                    }
                    applyToolResultBudget(request.turnId(), messages, actions, toolResults);
                    continue;
                }

                if (!(step instanceof AssistantStep as)) {
                    return modelErrorResult(messages, actions, "Unknown AgentStep type", step.getClass().getName());
                }

                if (as.content().isBlank()) {
                    emptyResponseCount++;
                    if (emptyResponseCount <= maxEmptyResponseRetries) {
                        appendMessage(request.turnId(), messages, actions,
                                new UserMessage(EMPTY_RESPONSE_CONTINUATION_PROMPT));
                        continue;
                    }
                    AssistantMessage fallback = new AssistantMessage(EMPTY_RESPONSE_MESSAGE);
                    appendMessage(request.turnId(), messages, actions, fallback);
                    return AgentTurnResult.emptyFallback(List.copyOf(messages), new TurnPersistencePlan(actions),
                            Optional.of(new EmptyFallbackDetails(Optional.of("empty_response_retry_exhausted"))));
                }

                emptyResponseCount = 0;
                switch (as.kind()) {
                    case FINAL, UNSPECIFIED -> {
                        AssistantMessage fm = new AssistantMessage(as.content(), as.usage(), UsageStaleness.fresh());
                        appendMessage(request.turnId(), messages, actions, fm);
                        return AgentTurnResult.finalResult(List.copyOf(messages), new TurnPersistencePlan(actions));
                    }
                    case PROGRESS -> {
                        AssistantProgressMessage pm = new AssistantProgressMessage(as.content(), as.usage(), UsageStaleness.fresh());
                        appendMessage(request.turnId(), messages, actions, pm);
                        appendMessage(request.turnId(), messages, actions, new UserMessage(PROGRESS_CONTINUATION_PROMPT));
                    }
                }
            }
            return AgentTurnResult.maxSteps(List.copyOf(messages), new TurnPersistencePlan(actions));
        } catch (CancellationRequestedException e) {
            return cancelledResult(request.turnId(), messages, actions, e.cancellation());
        }
    }

    private AgentStep nextWithRetries(List<ChatMessage> messages, CancellationToken token) {
        RuntimeException last = null;
        for (int i = 0; i < MODEL_REQUEST_ATTEMPTS; i++) {
            try { return modelAdapter.next(messages); }
            catch (RuntimeException e) { last = e; }
            token.throwIfCancellationRequested(CancellationPhase.MODEL_REQUEST);
        }
        throw Objects.requireNonNull(last, "lastException");
    }

    private AutoCompactResult autoCompactPreflight(String turnId, List<ChatMessage> messages,
                                                    List<PersistenceAction> actions, ContextStats stats) {
        if (autoCompactController.willAttempt(List.copyOf(messages), stats)) {
            publishEvent(new AgentEvent.AutoCompactEvent(turnId, Instant.now(), AutoCompactEventType.STARTED, Optional.empty(), Optional.empty()));
        }
        AutoCompactResult result = autoCompactController.preflight(List.copyOf(messages), stats, modelAdapter);
        switch (result.status()) {
            case COMPACTED -> {
                publishEvent(new AgentEvent.AutoCompactEvent(turnId, Instant.now(), AutoCompactEventType.COMPLETED, Optional.of(result.compressionResult()), Optional.empty()));
                CompressionBoundaryResult b = result.boundary().orElseThrow();
                actions.add(new PersistenceAction.AppendCompactBoundaryAction(b.summaryMessage(), b.metadata()));
                List<ChatMessage> retained = retainedAfterBoundary(result.messages(), b.summaryMessage());
                if (!retained.isEmpty()) actions.add(new PersistenceAction.AppendMessagesAction(retained));
            }
            case FAILED -> publishEvent(new AgentEvent.AutoCompactEvent(turnId, Instant.now(), AutoCompactEventType.FAILED, Optional.empty(), Optional.of(result.reason().orElse("auto compact failed"))));
            case SKIPPED -> { /* quiet */ }
        }
        return result;
    }

    private List<ChatMessage> retainedAfterBoundary(List<ChatMessage> compacted, ChatMessage summary) {
        boolean skipped = false;
        List<ChatMessage> retained = new ArrayList<>();
        for (ChatMessage m : compacted) {
            if (m instanceof SystemMessage) continue;
            if (!skipped && m.equals(summary)) { skipped = true; continue; }
            retained.add(m);
        }
        return List.copyOf(retained);
    }

    private void appendMessage(String turnId, List<ChatMessage> messages, List<PersistenceAction> actions, ChatMessage msg) {
        messages.add(msg);
        actions.add(new PersistenceAction.AppendMessagesAction(List.of(msg)));
        publishEvent(new AgentEvent.AssistantMessageEvent(turnId, Instant.now(), msg));
    }

    private void appendToolCallMessage(String turnId, List<ChatMessage> messages, List<PersistenceAction> actions,
                                        ToolCall call, Optional<code4j.model.ProviderUsage> usage) {
        appendMessage(turnId, messages, actions, new AssistantToolCallMessage(call.id(), call.toolName(), call.input(), usage, UsageStaleness.fresh()));
    }

    private ToolResultMessage appendToolResultMessage(String turnId, List<ChatMessage> messages,
                                                       List<PersistenceAction> actions, ToolCall call, ToolResult result) {
        ToolResultMessage original = new ToolResultMessage(call.id(), call.toolName(), result.content(), result.error());
        ToolResultReplacementResult rr = contextManager.replaceLargeToolResult(original);
        ToolResultMessage msg = rr.message();
        appendMessage(turnId, messages, actions, msg);
        publishEvent(new AgentEvent.ToolFinishedEvent(turnId, Instant.now(), call.id(), call.toolName(), result.error(), result.awaitUser(), rr.replacement()));
        return msg;
    }

    private void applyToolResultBudget(String turnId, List<ChatMessage> messages, List<PersistenceAction> actions,
                                        List<ToolResultMessage> toolResults) {
        ToolResultBudgetResult br = contextManager.applyToolResultBudget(List.copyOf(toolResults));
        if (!br.replacements().isEmpty()) {
            publishEvent(new ToolResultsBudgetedEvent(turnId, Instant.now(), br.replacements()));
        }
        toolResults.clear();
        toolResults.addAll(br.results());
    }

    private void appendToolCallsStepProjection(String turnId, List<ChatMessage> messages,
                                                List<PersistenceAction> actions, ToolCallsStep step) {
        step.content().filter(c -> !c.isBlank()).ifPresent(content -> {
            ChatMessage projected = switch (step.contentKind()) {
                case PROGRESS -> new AssistantProgressMessage(content);
                case UNSPECIFIED -> new AssistantMessage(content);
            };
            appendMessage(turnId, messages, actions, projected);
            if (step.contentKind() == ContentKind.PROGRESS) {
                appendMessage(turnId, messages, actions, new UserMessage(PROGRESS_CONTINUATION_PROMPT));
            }
        });
    }

    private String awaitUserQuestion(String toolUseId, List<ToolResultMessage> results) {
        return results.stream().filter(m -> m.toolUseId().equals(toolUseId)).findFirst().map(ToolResultMessage::content).orElse("");
    }

    private ToolContext createToolContext(AgentTurnRequest request, String toolUseId) {
        return new ToolContext(request.cwd(), request.sessionId(), Optional.of(request.turnId()), Optional.of(toolUseId), request.cancellationToken());
    }

    private void publishEvent(AgentEvent event) {
        try { eventSink.onEvent(event); } catch (RuntimeException ignored) {}
    }

    private AgentTurnResult modelErrorResult(List<ChatMessage> messages, List<PersistenceAction> actions,
                                              String errorMsg, String causeClass) {
        return AgentTurnResult.modelError(List.copyOf(messages), new TurnPersistencePlan(actions),
                new ModelErrorDetails(new TurnError(errorMsg, TurnErrorSource.MODEL, false, Optional.empty(), Optional.of(causeClass))));
    }

    private AgentTurnResult modelErrorResult(List<ChatMessage> messages, List<PersistenceAction> actions,
                                              ModelRequestException e) {
        String msg = e.getMessage() == null || e.getMessage().isBlank() ? "Model request failed" : e.getMessage();
        return AgentTurnResult.modelError(List.copyOf(messages), new TurnPersistencePlan(actions),
                new ModelErrorDetails(new TurnError(msg, TurnErrorSource.MODEL, e.retryable(), e.diagnostics(),
                        Optional.of(ModelRequestException.class.getName()))));
    }

    private AgentTurnResult cancelledResult(String turnId, List<ChatMessage> messages, List<PersistenceAction> actions,
                                             TurnCancellation cancellation) {
        publishEvent(new AgentEvent.TurnCancelledEvent(turnId, Instant.now(), cancellation));
        return AgentTurnResult.cancelled(List.copyOf(messages), new TurnPersistencePlan(actions), new CancellationDetails(cancellation));
    }
}
