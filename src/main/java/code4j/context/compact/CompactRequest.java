package code4j.context.compact;

import code4j.core.loop.ModelAdapter;
import code4j.core.message.ChatMessage;

import java.util.List;
import java.util.Objects;

public record CompactRequest(List<ChatMessage> messages, ModelAdapter modelAdapter, CompactTrigger trigger) {
    public CompactRequest {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        modelAdapter = Objects.requireNonNull(modelAdapter, "modelAdapter");
        trigger = Objects.requireNonNull(trigger, "trigger");
    }
}
