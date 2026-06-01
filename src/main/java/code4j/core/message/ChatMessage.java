package code4j.core.message;

/**
 * Sealed hierarchy of all messages in an agent conversation.
 * <p>
 * Java's {@code sealed} keyword means the compiler knows every possible subtype.
 * Any {@code switch} over ChatMessage that misses a case is a compile error,
 * which makes refactoring safe — you can't accidentally forget a message type.
 * <p>
 * All subtypes are {@code record}s (immutable, thread-safe value objects).
 */
public sealed interface ChatMessage permits SystemMessage, UserMessage, AssistantThinkingMessage,
        AssistantMessage, AssistantProgressMessage, AssistantToolCallMessage, ToolResultMessage,
        ContextSummaryMessage {
}
