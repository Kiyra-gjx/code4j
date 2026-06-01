package code4j.core.event;

import code4j.tools.result.ToolResultReplacementRecord;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Emitted when tool results exceeded the context budget and were replaced
 * with shorter versions. The original results are stored on disk.
 */
public record ToolResultsBudgetedEvent(String turnId, Instant timestamp,
                                       List<ToolResultReplacementRecord> replacements) implements AgentEvent {
    public ToolResultsBudgetedEvent {
        if (Objects.requireNonNull(turnId, "turnId").isBlank()) {
            throw new IllegalArgumentException("turnId must not be blank");
        }
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        replacements = List.copyOf(Objects.requireNonNull(replacements, "replacements"));
    }
}
