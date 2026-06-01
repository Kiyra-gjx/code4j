package code4j.core.step;

import code4j.model.ProviderThinkingBlock;
import code4j.model.ProviderUsage;
import code4j.model.StepDiagnostics;
import code4j.tools.api.ToolCall;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The model requested one or more tool invocations.
 * Must contain at least one {@link ToolCall}.
 */
public record ToolCallsStep(List<ToolCall> calls, Optional<String> content, ContentKind contentKind,
                            List<ProviderThinkingBlock> thinkingBlocks,
                            Optional<StepDiagnostics> diagnostics,
                            Optional<ProviderUsage> usage) implements AgentStep {
    public ToolCallsStep {
        calls = List.copyOf(Objects.requireNonNull(calls, "calls"));
        if (calls.isEmpty()) {
            throw new IllegalArgumentException("tool calls step requires at least one call");
        }
        content = Objects.requireNonNull(content, "content");
        contentKind = Objects.requireNonNull(contentKind, "contentKind");
        thinkingBlocks = List.copyOf(Objects.requireNonNull(thinkingBlocks, "thinkingBlocks"));
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        usage = Objects.requireNonNull(usage, "usage");
    }
}
