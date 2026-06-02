package code4j.tools.builtin;

import code4j.tools.api.Tool;
import code4j.tools.api.ToolContext;
import code4j.tools.api.ValidationResult;
import code4j.tools.metadata.*;
import code4j.tools.result.ToolResult;
import code4j.tools.validation.ToolInputValidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.util.Set;

public final class AskUserTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createSchema();
    private static final ToolMetadata METADATA = new ToolMetadata("ask_user", "Ask the user a question and pause the current turn.", INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.ASK_USER), ToolStatus.AVAILABLE);

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override public ValidationResult validateInput(JsonNode input) { return ToolInputValidation.object(input).requiredString("question").build(); }

    @Override public ToolResult run(JsonNode input, ToolContext ctx) { return new ToolResult("Question: " + input.get("question").asText(), false, true, java.util.Optional.empty()); }

    private static ObjectNode createSchema() { ObjectNode s = JSON.objectNode(); s.put("type", "object"); s.putObject("properties").putObject("question").put("type", "string").put("description", "The question to ask."); s.putArray("required").add("question"); return s; }
}
