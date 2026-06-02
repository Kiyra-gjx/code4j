package code4j.tools.builtin;

import code4j.core.turn.CancellationPhase;
import code4j.permissions.model.PathIntent;
import code4j.tools.api.Tool;
import code4j.tools.api.ToolContext;
import code4j.tools.api.ValidationResult;
import code4j.tools.metadata.*;
import code4j.tools.result.ToolResult;
import code4j.tools.validation.ToolInputValidation;
import code4j.workspace.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public final class EditFileTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createSchema();
    private static final ToolMetadata METADATA = new ToolMetadata("edit_file", "Edit a file by exact text replacement.", INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.WRITE), ToolStatus.AVAILABLE);

    private final WorkspacePathResolver pathResolver;

    public EditFileTool(WorkspacePathResolver pathResolver) { this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver"); }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input).pathField("path", true).custom((raw, b) -> {
            JsonNode oldText = raw != null && raw.isObject() ? raw.get("oldText") : null;
            if (oldText == null || oldText.isNull() || !oldText.isTextual() || oldText.asText().isEmpty()) b.addError("oldText must be a non-empty string");
            else b.normalized().put("oldText", oldText.asText());
            JsonNode newText = raw != null && raw.isObject() ? raw.get("newText") : null;
            if (newText == null || newText.isNull() || !newText.isTextual()) b.addError("newText must be a string");
            else b.normalized().put("newText", newText.asText());
            b.normalized().put("replaceAll", raw != null && raw.isObject() && raw.has("replaceAll") && raw.get("replaceAll").isBoolean() && raw.get("replaceAll").asBoolean());
        }).build();
    }

    @Override public ToolResult run(JsonNode input, ToolContext ctx) {
        String path = input.get("path").asText();
        String oldText = input.get("oldText").asText();
        String newText = input.get("newText").asText();
        boolean replaceAll = input.get("replaceAll").asBoolean();
        try {
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            WorkspacePathResult resolved = pathResolver.resolve(new WorkspacePathRequest(ctx.cwd(), path, PathIntent.WRITE, WorkspacePathPolicy.EXISTING_FILE));
            Path target = resolved.resolvedPath().normalizedPath();
            String original = Files.readString(target, StandardCharsets.UTF_8);
            int first = original.indexOf(oldText);
            if (first < 0) return ToolResult.error("oldText not found in " + path);
            String next = replaceAll ? original.replace(oldText, newText) : original.substring(0, first) + newText + original.substring(first + oldText.length());
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            Files.writeString(target, next, StandardCharsets.UTF_8);
            return ToolResult.ok("EDITED: " + target);
        } catch (WorkspacePathException | IOException e) { return ToolResult.error(e.getMessage()); }
    }

    private static ObjectNode createSchema() { ObjectNode s = JSON.objectNode(); s.put("type", "object"); var p = s.putObject("properties"); p.putObject("path").put("type", "string"); p.putObject("oldText").put("type", "string"); p.putObject("newText").put("type", "string"); p.putObject("replaceAll").put("type", "boolean"); var r = s.putArray("required"); r.add("path"); r.add("oldText"); r.add("newText"); return s; }
}
