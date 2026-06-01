package code4j.core.message;

import java.util.Objects;

/**
 * A message from the user. Simple text content, no metadata attached.
 */
public record UserMessage(String content) implements ChatMessage {
    public UserMessage {
        content = Objects.requireNonNull(content, "content");
    }
}
