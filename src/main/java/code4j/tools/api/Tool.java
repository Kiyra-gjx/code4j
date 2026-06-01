package code4j.tools.api;

import code4j.tools.metadata.ToolMetadata;
import code4j.tools.result.ToolResult;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A tool that the agent can invoke.
 * <p>
 * Each tool provides:
 * <ul>
 *   <li>{@link #metadata()} — name, description, capabilities for the system prompt</li>
 *   <li>{@link #inputSchema()} — JSON Schema defining valid input</li>
 *   <li>{@link #validateInput(JsonNode)} — normalizes and validates raw model input</li>
 *   <li>{@link #run(JsonNode, ToolContext)} — executes the tool</li>
 * </ul>
 */
public interface Tool {
    ToolMetadata metadata();

    JsonNode inputSchema();

    ValidationResult validateInput(JsonNode input);

    ToolResult run(JsonNode normalizedInput, ToolContext toolContext);
}
