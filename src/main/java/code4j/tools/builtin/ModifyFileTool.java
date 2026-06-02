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

public final class ModifyFileTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createSchema();
    private static final ToolMetadata METADATA = new ToolMetadata("modify_file", "Create or replace a file with full content.", INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.WRITE), ToolStatus.AVAILABLE);

    private final WorkspacePathResolver pathResolver;

    public ModifyFileTool(WorkspacePathResolver pathResolver) { this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver"); }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input).pathField("path", true).custom((raw, b) -> {
            JsonNode c = raw != null && raw.isObject() ? raw.get("content") : null;
            if (c == null || c.isNull() || !c.isTextual()) b.addError("content must be a string");
            else b.normalized().put("content", c.asText());
        }).build();
    }

    @Override public ToolResult run(JsonNode input, ToolContext ctx) {
        String path = input.get("path").asText();
        String content = input.get("content").asText();
        try {
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            WorkspacePathResult resolved = pathResolver.resolve(new WorkspacePathRequest(ctx.cwd(), path, PathIntent.WRITE, WorkspacePathPolicy.TARGET_OR_EXISTING_PARENT));
            Path target = resolved.resolvedPath().normalizedPath();
            boolean existed = Files.exists(target);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            return ToolResult.ok((existed ? "MODIFIED: " : "CREATED: ") + target + "\nCHARS: " + content.length());
        } catch (WorkspacePathException | IOException e) { return ToolResult.error(e.getMessage()); }
    }

    private static ObjectNode createSchema() { ObjectNode s = JSON.objectNode(); s.put("type", "object"); var p = s.putObject("properties"); p.putObject("path").put("type", "string"); p.putObject("content").put("type", "string"); var r = s.putArray("required"); r.add("path"); r.add("content"); return s; }
}
