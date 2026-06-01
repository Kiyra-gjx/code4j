package code4j.context.compact;

import java.time.Instant;
import java.util.Objects;

public record CompactMetadata(CompactTrigger trigger, long tokensBefore, long tokensAfter,
                               int messageCount, Instant timestamp) {
    public CompactMetadata {
        trigger = Objects.requireNonNull(trigger, "trigger");
        if (tokensBefore < 0 || tokensAfter < 0) {
            throw new IllegalArgumentException("token counts must be non-negative");
        }
        if (messageCount < 0) {
            throw new IllegalArgumentException("messageCount must be non-negative");
        }
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    public int compressedCount() { return messageCount; }
}
