package code4j.context.compact;

import code4j.core.message.ChatMessage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CompressionResult(List<ChatMessage> messages, Optional<CompressionBoundaryResult> boundary) {
    public CompressionResult {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        boundary = Objects.requireNonNull(boundary, "boundary");
    }
}
