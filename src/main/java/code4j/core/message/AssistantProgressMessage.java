package code4j.core.message;

import code4j.model.ProviderUsage;
import code4j.model.UsageStaleness;

import java.util.Objects;
import java.util.Optional;

/**
 * Streaming text that arrives before the final {@link AssistantMessage}.
 * These are intermediate deltas shown to the user in real time.
 * Once the stream completes, a final {@link AssistantMessage} is emitted.
 */
public record AssistantProgressMessage(String content, Optional<ProviderUsage> providerUsage,
                                       UsageStaleness usageStaleness) implements ChatMessage {
    public AssistantProgressMessage {
        content = Objects.requireNonNull(content, "content");
        providerUsage = Objects.requireNonNull(providerUsage, "providerUsage");
        usageStaleness = Objects.requireNonNull(usageStaleness, "usageStaleness");
    }

    public AssistantProgressMessage(String content) {
        this(content, Optional.empty(), UsageStaleness.fresh());
    }
}
