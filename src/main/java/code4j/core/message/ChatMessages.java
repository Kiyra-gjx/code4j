package code4j.core.message;

import code4j.model.ProviderUsage;
import code4j.model.UsageStaleness;

import java.util.Objects;
import java.util.Optional;

/**
 * Static utility methods for working with {@link ChatMessage} instances.
 * <p>
 * Because all message types are immutable records, "modification" always
 * means creating a new instance. These methods encapsulate the pattern
 * of copying a message with updated usage metadata.
 */
public final class ChatMessages {
    private ChatMessages() {
    }

    /**
     * Attaches provider token usage to a message.
     * Only messages that carry usage fields (AssistantMessage, AssistantProgressMessage,
     * AssistantToolCallMessage) are affected; other types pass through unchanged.
     */
    public static ChatMessage withProviderUsage(ChatMessage message, ProviderUsage usage) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(usage, "usage");
        return switch (message) {
            case AssistantMessage m -> new AssistantMessage(m.content(), Optional.of(usage), m.usageStaleness());
            case AssistantProgressMessage m -> new AssistantProgressMessage(m.content(), Optional.of(usage), m.usageStaleness());
            case AssistantToolCallMessage m -> new AssistantToolCallMessage(m.toolUseId(), m.toolName(), m.input(), Optional.of(usage), m.usageStaleness());
            default -> message;
        };
    }

    /**
     * Marks the usage data on a message as stale, typically after context compaction.
     * Stale usage means the token counts no longer reflect the current conversation state.
     */
    public static ChatMessage markUsageStale(ChatMessage message, String reason) {
        Objects.requireNonNull(message, "message");
        UsageStaleness stale = UsageStaleness.stale(reason);
        return switch (message) {
            case AssistantMessage m -> new AssistantMessage(m.content(), m.providerUsage(), stale);
            case AssistantProgressMessage m -> new AssistantProgressMessage(m.content(), m.providerUsage(), stale);
            case AssistantToolCallMessage m -> new AssistantToolCallMessage(m.toolUseId(), m.toolName(), m.input(), m.providerUsage(), stale);
            default -> message;
        };
    }
}
