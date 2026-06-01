package code4j.core.message;

import java.time.Instant;
import java.util.Objects;

/**
 * A synthetic message injected after context compaction.
 * It summarizes {@code compressedCount} previous messages that were removed
 * from the conversation to stay within the model's context window.
 * <p>
 * This message tells the model "here's what happened earlier" without
 * consuming the full token budget of the original messages.
 */
public record ContextSummaryMessage(String content, int compressedCount, Instant timestamp) implements ChatMessage {
    public ContextSummaryMessage {
        content = Objects.requireNonNull(content, "content");
        if (compressedCount < 0) {
            throw new IllegalArgumentException("compressedCount must be non-negative");
        }
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }
}
