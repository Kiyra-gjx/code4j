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

public final class PatchFileTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createSchema();
    private static final ToolMetadata METADATA = new ToolMetadata("patch_file", "Apply multiple text replacements to a file.", INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.WRITE), ToolStatus.AVAILABLE);

    private final WorkspacePathResolver pathResolver;

    public PatchFileTool(WorkspacePathResolver pathResolver) { this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver"); }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input).pathField("path", true).custom((raw, b) -> {
            JsonNode reps = raw != null && raw.isObject() ? raw.get("replacements") : null;
            if (reps == null || !reps.isArray() || reps.isEmpty()) { b.addError("replacements must be a non-empty array"); return; }
            var arr = b.normalized().putArray("replacements");
            for (JsonNode r : reps) {
                var item = arr.addObject();
                JsonNode ot = r.get("oldText"); JsonNode nt = r.get("newText");
                if (ot == null || !ot.isTextual() || ot.asText().isEmpty()) { b.addError("each replacement needs non-empty oldText"); continue; }
                if (nt == null || !nt.isTextual()) { b.addError("each replacement needs string newText"); continue; }
                item.put("oldText", ot.asText()); item.put("newText", nt.asText());
            }
        }).build();
    }

    @Override public ToolResult run(JsonNode input, ToolContext ctx) {
        String path = input.get("path").asText();
        try {
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            WorkspacePathResult resolved = pathResolver.resolve(new WorkspacePathRequest(ctx.cwd(), path, PathIntent.WRITE, WorkspacePathPolicy.EXISTING_FILE));
            Path target = resolved.resolvedPath().normalizedPath();
            String content = Files.readString(target, StandardCharsets.UTF_8);
            int count = 0;
            for (JsonNode r : input.get("replacements")) {
                String ot = r.get("oldText").asText(), nt = r.get("newText").asText();
                int idx = content.indexOf(ot);
                if (idx < 0) return ToolResult.error("oldText not found: " + ot.substring(0, Math.min(80, ot.length())));
                content = content.substring(0, idx) + nt + content.substring(idx + ot.length());
                count++;
            }
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return ToolResult.ok("PATCHED: " + target + "\nREPLACEMENTS: " + count);
        } catch (WorkspacePathException | IOException e) { return ToolResult.error(e.getMessage()); }
    }

    private static ObjectNode createSchema() { ObjectNode s = JSON.objectNode(); s.put("type", "object"); var p = s.putObject("properties"); p.putObject("path").put("type", "string"); var reps = p.putObject("replacements"); reps.put("type", "array"); var items = reps.putObject("items"); items.put("type", "object"); var ip = items.putObject("properties"); ip.putObject("oldText").put("type", "string"); ip.putObject("newText").put("type", "string"); var r = s.putArray("required"); r.add("path"); r.add("replacements"); return s; }
}
