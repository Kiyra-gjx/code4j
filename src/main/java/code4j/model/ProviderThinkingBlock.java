package code4j.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * A single thinking block from an extended-thinking model response.
 * The {@code type} field corresponds to the Anthropic content block type
 * (e.g. "thinking", "redacted_thinking"), and {@code raw} holds the
 * full JSON as returned by the provider.
 */
public record ProviderThinkingBlock(String type, JsonNode raw) {
    public ProviderThinkingBlock {
        if (Objects.requireNonNull(type, "type").isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        raw = Objects.requireNonNull(raw, "raw");
    }
}
