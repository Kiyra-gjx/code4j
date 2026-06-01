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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class GrepFilesTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 2_000;
    private static final ObjectNode INPUT_SCHEMA = createInputSchema();
    private static final ToolMetadata METADATA = new ToolMetadata(
            "grep_files",
            "Search for a regex pattern in files under a workspace directory.",
            INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.READ), ToolStatus.AVAILABLE);

    private final WorkspacePathResolver workspacePathResolver;

    public GrepFilesTool() {
        this(new WorkspacePathResolver());
    }

    public GrepFilesTool(WorkspacePathResolver workspacePathResolver) {
        this.workspacePathResolver = Objects.requireNonNull(workspacePathResolver, "workspacePathResolver");
    }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override
    public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input)
                .requiredString("pattern")
                .pathField("path", false)
                .optionalBoolean("caseSensitive")
                .optionalInteger("limit", 1, MAX_LIMIT)
                .custom((rawInput, builder) -> {
                    try {
                        int flags = builder.normalized().has("caseSensitive")
                                && builder.normalized().get("caseSensitive").asBoolean() ? 0 : Pattern.CASE_INSENSITIVE;
                        Pattern.compile(builder.normalized().get("pattern").asText(), flags);
                    } catch (PatternSyntaxException e) {
                        builder.addError("pattern is not a valid regex: " + e.getMessage());
                    }
                })
                .build();
    }

    @Override
    public ToolResult run(JsonNode normalizedInput, ToolContext toolContext) {
        String pattern = normalizedInput.get("pattern").asText();
        String inputPath = normalizedInput.has("path") ? normalizedInput.get("path").asText() : ".";
        int limit = normalizedInput.has("limit") ? normalizedInput.get("limit").asInt() : DEFAULT_LIMIT;
        boolean caseSensitive = normalizedInput.has("caseSensitive") && normalizedInput.get("caseSensitive").asBoolean();

        try {
            toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            WorkspacePathResult base = workspacePathResolver.resolve(
                    new WorkspacePathRequest(toolContext.cwd(), inputPath, PathIntent.SEARCH, true, true));
            toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);

            Pattern compiled = Pattern.compile(pattern, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            List<String> matches = new ArrayList<>();
            boolean truncated = search(base.resolvedPath().normalizedPath(), compiled, limit, matches, toolContext);

            StringBuilder sb = new StringBuilder();
            sb.append("PATTERN: ").append(pattern).append('\n');
            sb.append("BASE: ").append(base.resolvedPath().normalizedPath()).append('\n');
            sb.append("COUNT: ").append(matches.size()).append('\n');
            sb.append("TRUNCATED: ").append(truncated).append('\n');
            matches.forEach(m -> sb.append(m).append('\n'));
            return ToolResult.ok(sb.toString());
        } catch (WorkspacePathException | IOException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private boolean search(Path directory, Pattern pattern, int limit, List<String> matches,
                           ToolContext toolContext) throws IOException {
        toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
        if (matches.size() >= limit) return true;

        List<Path> children = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
                children.add(child);
            }
        }
        children.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));

        for (Path child : children) {
            if (matches.size() >= limit) return true;
            if (Files.isDirectory(child)) {
                if (!isHidden(child) && !"node_modules".equals(child.getFileName().toString())
                        && !".git".equals(child.getFileName().toString())) {
                    boolean truncated = search(child, pattern, limit, matches, toolContext);
                    if (truncated) return true;
                }
            } else if (Files.isRegularFile(child) && !isBinary(child)) {
                try {
                    List<String> lines = Files.readAllLines(child, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size() && matches.size() < limit; i++) {
                        if (pattern.matcher(lines.get(i)).find()) {
                            matches.add(directory.relativize(child).toString().replace('\\', '/')
                                    + ":" + (i + 1) + ": " + lines.get(i));
                        }
                    }
                } catch (IOException ignored) {
                    // Skip unreadable files
                }
            }
        }
        return matches.size() >= limit;
    }

    private static boolean isHidden(Path path) {
        Path name = path.getFileName();
        return name != null && name.toString().startsWith(".");
    }

    private static boolean isBinary(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            for (int i = 0; i < Math.min(bytes.length, 8000); i++) {
                if (bytes[i] == 0) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static ObjectNode createInputSchema() {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("pattern").put("type", "string").put("description", "Regex pattern to search for.");
        props.putObject("path").put("type", "string").put("description", "Directory to search in.");
        props.putObject("caseSensitive").put("type", "boolean");
        props.putObject("limit").put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT);
        ArrayNode required = schema.putArray("required");
        required.add("pattern");
        return schema;
    }
}
