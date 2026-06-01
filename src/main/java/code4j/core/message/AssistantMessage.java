package code4j.core.message;

import code4j.model.ProviderUsage;
import code4j.model.UsageStaleness;

import java.util.Objects;
import java.util.Optional;

/**
 * The final text response from the assistant — the message shown to the user
 * after thinking, tool calls, and streaming are complete.
 * <p>
 * {@code providerUsage} is Optional because not every provider returns token counts,
 * and because usage can be attached after the message is first created.
 * {@code usageStaleness} tracks whether the usage data is still valid (it becomes
 * stale after context compaction).
 */
public record AssistantMessage(String content, Optional<ProviderUsage> providerUsage,
                               UsageStaleness usageStaleness) implements ChatMessage {
    public AssistantMessage {
        content = Objects.requireNonNull(content, "content");
        providerUsage = Objects.requireNonNull(providerUsage, "providerUsage");
        usageStaleness = Objects.requireNonNull(usageStaleness, "usageStaleness");
    }

    public AssistantMessage(String content) {
        this(content, Optional.empty(), UsageStaleness.fresh());
    }
}
