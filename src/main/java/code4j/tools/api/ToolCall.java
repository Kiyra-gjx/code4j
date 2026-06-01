package code4j.tools.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * A tool invocation request from the model.
 * Contains enough information to look up the tool, validate input, and execute it.
 */
public record ToolCall(String id, String toolName, JsonNode input) {
    public ToolCall {
        requireText(id, "id");
        requireText(toolName, "toolName");
        input = Objects.requireNonNull(input, "input");
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
