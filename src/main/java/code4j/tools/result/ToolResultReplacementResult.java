package code4j.tools.result;

import code4j.core.message.ToolResultMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * A tool result message optionally paired with a replacement record
 * indicating that the original result was too large and was stored on disk.
 */
public record ToolResultReplacementResult(ToolResultMessage message,
                                          Optional<ToolResultReplacementRecord> replacement) {
    public ToolResultReplacementResult {
        message = Objects.requireNonNull(message, "message");
        replacement = Objects.requireNonNull(replacement, "replacement");
        if (replacement.isPresent()) {
            ToolResultReplacementRecord record = replacement.get();
            if (!message.content().equals(record.replacementContent())) {
                throw new IllegalArgumentException(
                        "replacement content must match tool result message content");
            }
        }
    }
}
