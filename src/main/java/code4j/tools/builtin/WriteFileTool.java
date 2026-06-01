package code4j.tools.builtin;

import code4j.core.turn.CancellationPhase;
import code4j.permissions.model.PathIntent;
import code4j.tools.api.Tool;
import code4j.tools.api.ToolContext;
import code4j.tools.api.ValidationResult;
import code4j.tools.metadata.ToolCapability;
import code4j.tools.metadata.ToolMetadata;
import code4j.tools.metadata.ToolOrigin;
import code4j.tools.metadata.ToolStatus;
import code4j.tools.result.ToolResult;
import code4j.tools.validation.ToolInputValidation;
import code4j.workspace.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Set;

public final class WriteFileTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createInputSchema();
    private static final ToolMetadata METADATA = new ToolMetadata(
            "write_file",
            "Create or overwrite a UTF-8 text file relative to the current workspace.",
            INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.WRITE), ToolStatus.AVAILABLE);

    private final WorkspacePathResolver workspacePathResolver;

    public WriteFileTool() {
        this(new WorkspacePathResolver());
    }

    public WriteFileTool(WorkspacePathResolver workspacePathResolver) {
        this.workspacePathResolver = Objects.requireNonNull(workspacePathResolver, "workspacePathResolver");
    }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override
    public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input)
                .pathField("path", true)
                .custom((rawInput, builder) -> {
                    JsonNode contentNode = rawInput != null && rawInput.isObject() ? rawInput.get("content") : null;
                    if (contentNode == null || contentNode.isNull() || !contentNode.isTextual()) {
                        builder.addError("content must exist and be a string");
                        return;
                    }
                    builder.normalized().put("content", contentNode.asText());
                })
                .build();
    }

    @Override
    public ToolResult run(JsonNode normalizedInput, ToolContext toolContext) {
        String inputPath = normalizedInput.get("path").asText();
        String content = normalizedInput.get("content").asText();
        try {
            toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            WorkspacePathResult resolved = workspacePathResolver.resolve(
                    new WorkspacePathRequest(toolContext.cwd(), inputPath, PathIntent.WRITE,
                            WorkspacePathPolicy.TARGET_OR_EXISTING_PARENT));
            Path targetPath = resolved.resolvedPath().normalizedPath();
            boolean existed = Files.exists(targetPath);

            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);

            return ToolResult.ok("WROTE: " + targetPath + "\nOPERATION: " + (existed ? "OVERWRITE" : "CREATE")
                    + "\nCHARS: " + content.length());
        } catch (WorkspacePathException | IOException e) {
            return ToolResult.error("Failed to write file " + inputPath + ": " + e.getMessage());
        }
    }

    private static ObjectNode createInputSchema() {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("path").put("type", "string").put("description", "File path to create or overwrite.");
        props.putObject("content").put("type", "string").put("description", "UTF-8 content to write.");
        ArrayNode required = schema.putArray("required");
        required.add("path");
        required.add("content");
        return schema;
    }
}
