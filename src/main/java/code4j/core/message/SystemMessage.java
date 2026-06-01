package code4j.core.message;

import java.util.Objects;

/**
 * The system prompt that sets the agent's behavior, rules, and tool definitions.
 * Typically the first message in a conversation.
 */
public record SystemMessage(String content) implements ChatMessage {
    public SystemMessage {
        content = Objects.requireNonNull(content, "content");
    }
}
