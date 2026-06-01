package code4j.core.event;

import code4j.context.compact.AutoCompactEventType;
import code4j.context.compact.CompressionResult;
import code4j.context.stats.ContextStats;
import code4j.core.message.ChatMessage;
import code4j.core.turn.TurnCancellation;
import code4j.tools.result.ToolResultReplacementRecord;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Events emitted during agent turn execution, consumed by the TUI/session layer.
 * <p>
 * Every event has a {@code turnId} (which turn it belongs to) and a {@code timestamp}.
 * Events are informational — they don't affect the agent loop's control flow.
 */
public sealed interface AgentEvent
        permits AgentEvent.AssistantMessageEvent, AgentEvent.ToolStartedEvent,
        AgentEvent.ToolFinishedEvent, AgentEvent.ContextStatsEvent,
        AgentEvent.AutoCompactEvent, AgentEvent.AwaitUserEvent,
        AgentEvent.TurnCancelledEvent, ToolResultsBudgetedEvent {

    String turnId();
    Instant timestamp();

    /** A message from the assistant (final text, thinking, tool call, etc.). */
    record AssistantMessageEvent(String turnId, Instant timestamp, ChatMessage message) implements AgentEvent {
        public AssistantMessageEvent {
            requireEvent(turnId, timestamp);
            message = Objects.requireNonNull(message, "message");
        }
    }

    /** A tool has started executing. */
    record ToolStartedEvent(String turnId, Instant timestamp, String toolUseId, String toolName,
                            JsonNode input) implements AgentEvent {
        public ToolStartedEvent {
            requireEvent(turnId, timestamp);
            requireText(toolUseId, "toolUseId");
            requireText(toolName, "toolName");
            input = Objects.requireNonNull(input, "input");
        }
    }

    /** A tool has finished executing. */
    record ToolFinishedEvent(String turnId, Instant timestamp, String toolUseId, String toolName,
                             boolean error, boolean awaitUser,
                             Optional<ToolResultReplacementRecord> replacement) implements AgentEvent {
        public ToolFinishedEvent {
            requireEvent(turnId, timestamp);
            requireText(toolUseId, "toolUseId");
            requireText(toolName, "toolName");
            replacement = Objects.requireNonNull(replacement, "replacement");
        }
    }

    /** Current context window usage statistics. */
    record ContextStatsEvent(String turnId, Instant timestamp, ContextStats stats) implements AgentEvent {
        public ContextStatsEvent {
            requireEvent(turnId, timestamp);
            stats = Objects.requireNonNull(stats, "stats");
        }
    }

    /** Auto-compaction lifecycle event. */
    record AutoCompactEvent(String turnId, Instant timestamp, AutoCompactEventType type,
                            Optional<CompressionResult> result, Optional<String> reason) implements AgentEvent {
        public AutoCompactEvent {
            requireEvent(turnId, timestamp);
            type = Objects.requireNonNull(type, "type");
            result = Objects.requireNonNull(result, "result");
            reason = Objects.requireNonNull(reason, "reason");
            if (type == AutoCompactEventType.COMPLETED && result.isEmpty()) {
                throw new IllegalArgumentException("COMPLETED auto compact event requires result");
            }
            if (type != AutoCompactEventType.COMPLETED && result.isPresent()) {
                throw new IllegalArgumentException(type + " auto compact event must not carry result");
            }
        }
    }

    /** The model called ask_user — waiting for user response. */
    record AwaitUserEvent(String turnId, Instant timestamp, String toolUseId, String question)
            implements AgentEvent {
        public AwaitUserEvent {
            requireEvent(turnId, timestamp);
            requireText(toolUseId, "toolUseId");
            requireText(question, "question");
        }
    }

    /** The turn was cancelled. */
    record TurnCancelledEvent(String turnId, Instant timestamp, TurnCancellation cancellation)
            implements AgentEvent {
        public TurnCancelledEvent {
            requireEvent(turnId, timestamp);
            cancellation = Objects.requireNonNull(cancellation, "cancellation");
        }
    }

    private static void requireEvent(String turnId, Instant timestamp) {
        requireText(turnId, "turnId");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
