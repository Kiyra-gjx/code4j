package code4j.core.message;

import code4j.model.ProviderThinkingBlock;

import java.util.List;
import java.util.Objects;

/**
 * Represents extended thinking content from the model (Anthropic's extended thinking feature).
 * Contains one or more thinking blocks, each with a type and raw JSON payload.
 * <p>
 * This is separate from {@link AssistantMessage} because thinking is ephemeral —
 * it may be redacted by the provider and is not guaranteed to persist across turns.
 */
public record AssistantThinkingMessage(List<ProviderThinkingBlock> blocks) implements ChatMessage {
    public AssistantThinkingMessage {
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }
}
