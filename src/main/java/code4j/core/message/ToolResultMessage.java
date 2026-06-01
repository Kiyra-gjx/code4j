package code4j.core.message;

import java.util.Objects;

/**
 * The result of executing a tool call.
 * {@code toolUseId} links back to the originating {@link AssistantToolCallMessage}.
 * {@code error} is true when the tool execution failed (the model uses this to decide
 * whether to retry, report failure, or try a different approach).
 */
public record ToolResultMessage(String toolUseId, String toolName, String content, boolean error) implements ChatMessage {
    public ToolResultMessage {
        requireText(toolUseId, "toolUseId");
        requireText(toolName, "toolName");
        content = Objects.requireNonNull(content, "content");
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
