package code4j.tools.metadata;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Set;

/**
 * Static metadata describing a tool — name, description, capabilities, and
 * JSON Schema for input — used to build the system prompt.
 */
public record ToolMetadata(String name, String description, JsonNode inputSchema, ToolOrigin origin,
                           Set<ToolCapability> capabilities, ToolStatus status) {
    public ToolMetadata {
        requireText(name, "name");
        description = Objects.requireNonNull(description, "description");
        inputSchema = Objects.requireNonNull(inputSchema, "inputSchema");
        origin = Objects.requireNonNull(origin, "origin");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        status = Objects.requireNonNull(status, "status");
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
