package code4j.tools.builtin;

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
import java.nio.file.NoSuchFileException;
import java.util.Objects;
import java.util.Set;

public final class ReadFileTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final int DEFAULT_READ_LIMIT = 12_000;
    private static final int MAX_READ_LIMIT = 20_000;
    private static final int DEFAULT_LINE_COUNT = 200;
    private static final int MAX_LINE_COUNT = 2_000;
    private static final ObjectNode INPUT_SCHEMA = createInputSchema();
    private static final ToolMetadata METADATA = new ToolMetadata(
            "read_file",
            "Read a UTF-8 text file relative to the current workspace.",
            INPUT_SCHEMA,
            ToolOrigin.BUILTIN,
            Set.of(ToolCapability.READ),
            ToolStatus.AVAILABLE
    );

    private final ReadFilePathAccess pathAccess;
    private final WorkspacePathResolver workspacePathResolver;

    public ReadFileTool() {
        this(ReadFilePathAccess.alwaysAllow(), new WorkspacePathResolver());
    }

    public ReadFileTool(ReadFilePathAccess pathAccess, WorkspacePathResolver workspacePathResolver) {
        this.pathAccess = Objects.requireNonNull(pathAccess, "pathAccess");
        this.workspacePathResolver = Objects.requireNonNull(workspacePathResolver, "workspacePathResolver");
    }

    @Override
    public ToolMetadata metadata() { return METADATA; }

    @Override
    public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override
    public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input)
                .pathField("path", true)
                .optionalInteger("offset", 0, Integer.MAX_VALUE)
                .optionalInteger("limit", 1, MAX_READ_LIMIT)
                .optionalInteger("lineStart", 1, Integer.MAX_VALUE)
                .optionalInteger("lineCount", 1, MAX_LINE_COUNT)
                .custom((rawInput, builder) -> {
                    boolean hasOffset = builder.normalized().has("offset");
                    boolean hasLimit = builder.normalized().has("limit");
                    boolean hasLineStart = builder.normalized().has("lineStart");
                    boolean hasLineCount = builder.normalized().has("lineCount");
                    if ((hasOffset || hasLimit) && (hasLineStart || hasLineCount)) {
                        builder.addError("char mode (offset/limit) and line mode (lineStart/lineCount) cannot be combined");
                    }
                    if (hasLineCount && !hasLineStart) {
                        builder.addError("line mode requires lineStart");
                    }
                })
                .build();
    }

    @Override
    public ToolResult run(JsonNode normalizedInput, ToolContext toolContext) {
        String inputPath = normalizedInput.get("path").asText();
        boolean lineMode = normalizedInput.has("lineStart") || normalizedInput.has("lineCount");
        try {
            WorkspacePathResult resolved = workspacePathResolver.resolve(
                    new WorkspacePathRequest(toolContext.cwd(), inputPath, PathIntent.READ, true, false));
            pathAccess.ensureReadAllowed(toolContext, resolved.resolvedPath());
            String content = Files.readString(resolved.resolvedPath().normalizedPath(), StandardCharsets.UTF_8);
            if (lineMode) {
                int start = normalizedInput.get("lineStart").asInt();
                int count = normalizedInput.has("lineCount") ? normalizedInput.get("lineCount").asInt() : DEFAULT_LINE_COUNT;
                return ToolResult.ok(lineChunk(inputPath, content, start, count));
            }
            int offset = normalizedInput.has("offset") ? normalizedInput.get("offset").asInt() : 0;
            int limit = normalizedInput.has("limit") ? normalizedInput.get("limit").asInt() : DEFAULT_READ_LIMIT;
            int start = Math.min(offset, content.length());
            int end = Math.min(content.length(), start + limit);
            boolean truncated = end < content.length();
            return ToolResult.ok(charHeader(inputPath, start, end, content.length(), truncated) + content.substring(start, end));
        } catch (NoSuchFileException e) {
            return ToolResult.error("File not found: " + inputPath);
        } catch (WorkspacePathException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Path does not exist:")) {
                return ToolResult.error("File not found: " + inputPath);
            }
            return ToolResult.error(e.getMessage());
        } catch (IOException e) {
            return ToolResult.error("Failed to read file " + inputPath + ": " + e.getMessage());
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            return ToolResult.error(msg == null || msg.isBlank() ? "Read file access denied" : msg);
        }
    }

    private static String lineChunk(String path, String content, int lineStart, int lineCount) {
        String[] lines = content.split("\\R", -1);
        int total = totalLines(lines, content);
        int startIdx = lineStart - 1;
        if (startIdx >= total) {
            return lineHeader(path, lineStart, total, total, false, total + 1);
        }
        int endExcl = (int) Math.min((long) total, (long) startIdx + lineCount);
        int lineEnd = endExcl > startIdx ? endExcl : lineStart - 1;
        boolean truncated = endExcl < total;
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < endExcl; i++) {
            sb.append(lines[i]).append('\n');
        }
        return lineHeader(path, lineStart, lineEnd, total, truncated, endExcl + 1) + sb;
    }

    private static int totalLines(String[] splitLines, String content) {
        if (content.isEmpty()) return 0;
        if (content.endsWith("\n") || content.endsWith("\r")) return Math.max(0, splitLines.length - 1);
        return splitLines.length;
    }

    private static String charHeader(String path, int offset, int end, int total, boolean truncated) {
        return "FILE: " + path + "\nMODE: chars\nOFFSET: " + offset + "\nEND: " + end
                + "\nTOTAL_CHARS: " + total + "\nTRUNCATED: " + (truncated ? "yes - call read_file again with offset " + end : "no") + "\n\n";
    }

    private static String lineHeader(String path, int lineStart, int lineEnd, int total, boolean truncated, int nextStart) {
        return "FILE: " + path + "\nMODE: lines\nLINE_START: " + lineStart + "\nLINE_END: " + lineEnd
                + "\nTOTAL_LINES: " + total + "\nTRUNCATED: " + (truncated ? "yes - call read_file again with lineStart " + nextStart : "no") + "\n\n";
    }

    private static ObjectNode createInputSchema() {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode path = props.putObject("path");
        path.put("type", "string");
        path.put("description", "Path to the UTF-8 text file.");
        ObjectNode offset = props.putObject("offset");
        offset.put("type", "integer").put("minimum", 0);
        ObjectNode limit = props.putObject("limit");
        limit.put("type", "integer").put("minimum", 1).put("maximum", MAX_READ_LIMIT);
        ObjectNode lineStart = props.putObject("lineStart");
        lineStart.put("type", "integer").put("minimum", 1);
        ObjectNode lineCount = props.putObject("lineCount");
        lineCount.put("type", "integer").put("minimum", 1).put("maximum", MAX_LINE_COUNT);
        ArrayNode required = schema.putArray("required");
        required.add("path");
        return schema;
    }
}
