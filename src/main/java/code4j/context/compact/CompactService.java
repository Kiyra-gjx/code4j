package code4j.context.compact;

import code4j.context.accounting.TokenAccountingService;
import code4j.core.loop.ModelAdapter;
import code4j.core.message.*;
import code4j.core.step.AgentStep;
import code4j.core.step.AssistantStep;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CompactService {
    private static final int MIN_KEEP_MESSAGES = 8;
    private static final long MAX_KEEP_TOKENS = 8_000;
    private static final int TOOL_RESULT_SUMMARY_PREVIEW_CHARS = 500;
    private static final String MANUAL_STALE_REASON = "conversation was manually compacted";
    private static final String AUTO_STALE_REASON = "conversation was automatically compacted";

    private final Clock clock;
    private final TokenAccountingService accountingService;

    public CompactService() {
        this(Clock.systemUTC());
    }

    public CompactService(Clock clock) {
        this(clock, new TokenAccountingService());
    }

    CompactService(Clock clock, TokenAccountingService accountingService) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.accountingService = Objects.requireNonNull(accountingService, "accountingService");
    }

    public ManualCompactResult compact(CompactRequest request) {
        Objects.requireNonNull(request, "request");
        List<ChatMessage> messages = request.messages();
        if (messages.size() <= MIN_KEEP_MESSAGES + 1) {
            return ManualCompactResult.skipped(messages, "not enough messages to compact");
        }
        int boundary = findRetentionBoundary(messages);
        if (boundary <= 1 || boundary >= messages.size()) {
            return ManualCompactResult.skipped(messages, "no compactable messages");
        }
        List<ChatMessage> toCompress = messages.subList(1, boundary);
        if (toCompress.isEmpty()) {
            return ManualCompactResult.skipped(messages, "no compactable messages");
        }
        long tokensBefore = accountingService.account(messages).totalTokens();
        ModelAdapter adapter = request.modelAdapter();
        List<ChatMessage> summaryRequest = List.of(
                new SystemMessage("Summarize this coding-agent conversation history for future continuation."),
                new UserMessage(buildSummaryPrompt(messagesToText(toCompress)))
        );
        String summaryContent;
        try {
            AgentStep step = adapter.next(summaryRequest);
            if (!(step instanceof AssistantStep as)) {
                return ManualCompactResult.failed(messages, "summary model returned tool calls");
            }
            summaryContent = as.content().trim();
            if (summaryContent.isBlank()) {
                return ManualCompactResult.failed(messages, "summary model returned empty content");
            }
        } catch (RuntimeException e) {
            return ManualCompactResult.failed(messages, safeReason(e));
        }
        ContextSummaryMessage summary = new ContextSummaryMessage(summaryContent, toCompress.size(), now());
        List<ChatMessage> compacted = new ArrayList<>();
        messages.stream().filter(SystemMessage.class::isInstance).forEach(compacted::add);
        compacted.add(summary);
        compacted.addAll(markRetainedUsageStale(messages.subList(boundary, messages.size()), request.trigger()));
        long tokensAfter = accountingService.account(compacted).totalTokens();
        CompactMetadata metadata = new CompactMetadata(request.trigger(), tokensBefore, tokensAfter,
                toCompress.size(), now());
        return ManualCompactResult.compacted(List.copyOf(compacted), new CompressionBoundaryResult(summary, metadata));
    }

    private Instant now() { return Instant.now(clock); }

    private static String safeReason(RuntimeException e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    private int findRetentionBoundary(List<ChatMessage> messages) {
        long tokenSum = 0;
        int boundary = messages.size();
        for (int i = messages.size() - 1; i >= 1; i--) {
            long tokens = accountingService.account(List.of(messages.get(i))).totalTokens();
            if (tokenSum + tokens > MAX_KEEP_TOKENS) break;
            tokenSum += tokens;
            boundary = i;
        }
        int minBoundary = Math.max(1, messages.size() - MIN_KEEP_MESSAGES);
        boundary = Math.min(boundary, minBoundary);
        if (boundary <= 1 && messages.size() > MIN_KEEP_MESSAGES + 1) {
            boundary = Math.max(1, messages.size() - MIN_KEEP_MESSAGES);
        }
        return alignBoundaryToToolRound(messages, boundary);
    }

    private static int alignBoundaryToToolRound(List<ChatMessage> messages, int boundary) {
        int start = 0;
        while (start < messages.size()) {
            int cursor = start;
            if (messages.get(cursor) instanceof AssistantThinkingMessage) cursor++;
            while (cursor < messages.size() && messages.get(cursor) instanceof AssistantToolCallMessage) cursor++;
            while (cursor < messages.size() && messages.get(cursor) instanceof ToolResultMessage) cursor++;
            if (cursor > start && hasToolRound(messages.subList(start, cursor))) {
                if (boundary > start && boundary < cursor) return start;
                start = cursor;
                continue;
            }
            start++;
        }
        return boundary;
    }

    private static boolean hasToolRound(List<ChatMessage> messages) {
        return messages.stream().anyMatch(m -> m instanceof AssistantToolCallMessage || m instanceof ToolResultMessage);
    }

    private static List<ChatMessage> markRetainedUsageStale(List<ChatMessage> messages, CompactTrigger trigger) {
        String reason = switch (trigger) {
            case AUTO -> AUTO_STALE_REASON;
            case MANUAL -> MANUAL_STALE_REASON;
            case MICRO -> "conversation was compacted";
        };
        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            result.add(ChatMessages.markUsageStale(message, reason));
        }
        return List.copyOf(result);
    }

    private static String messagesToText(List<ChatMessage> messages) {
        List<String> parts = new ArrayList<>();
        for (ChatMessage message : messages) {
            switch (message) {
                case UserMessage u -> parts.add("[User]: " + u.content());
                case AssistantMessage a -> parts.add("[Assistant]: " + a.content());
                case AssistantProgressMessage p -> parts.add("[Progress]: " + p.content());
                case AssistantThinkingMessage ignored -> parts.add("[Thinking]: omitted");
                case AssistantToolCallMessage tc -> parts.add("[Tool: " + tc.toolName() + "]: " + tc.input());
                case ToolResultMessage tr -> parts.add("[Result: " + tr.toolName()
                        + (tr.error() ? " ERROR" : "") + "]: " + preview(tr.content()));
                case ContextSummaryMessage cs -> parts.add("[Summary]: " + cs.content());
                case SystemMessage ignored -> {}
                default -> {}
            }
        }
        return String.join("\n\n", parts);
    }

    private static String preview(String content) {
        if (content.length() <= TOOL_RESULT_SUMMARY_PREVIEW_CHARS) return content;
        return content.substring(0, TOOL_RESULT_SUMMARY_PREVIEW_CHARS) + "... (truncated)";
    }

    private static String buildSummaryPrompt(String text) {
        return """
                Summarize the following coding-agent conversation history. Preserve: user goals, decisions made,
                files/tools/results that matter, unresolved tasks and next steps, errors not to repeat.
                Keep the summary concise but operational.

                Conversation:
                %s
                """.formatted(text);
    }
}
