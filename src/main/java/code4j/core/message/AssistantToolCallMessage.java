package code4j.core.message;

import code4j.model.ProviderUsage;
import code4j.model.UsageStaleness;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Optional;

/**
 * A tool-use request from the assistant. The model decided to invoke a tool
 * rather than produce text. Contains the tool name, input arguments as JSON,
 * and a unique {@code toolUseId} that links it to the corresponding
 * {@link ToolResultMessage}.
 */
public record AssistantToolCallMessage(String toolUseId, String toolName, JsonNode input,
                                       Optional<ProviderUsage> providerUsage,
                                       UsageStaleness usageStaleness) implements ChatMessage {
    public AssistantToolCallMessage {
        requireText(toolUseId, "toolUseId");
        requireText(toolName, "toolName");
        input = Objects.requireNonNull(input, "input");
        providerUsage = Objects.requireNonNull(providerUsage, "providerUsage");
        usageStaleness = Objects.requireNonNull(usageStaleness, "usageStaleness");
    }

    public AssistantToolCallMessage(String toolUseId, String toolName, JsonNode input) {
        this(toolUseId, toolName, input, Optional.empty(), UsageStaleness.fresh());
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
