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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ListFilesTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final int DEFAULT_MAX_DEPTH = 2;
    private static final int MAX_DEPTH = 10;
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 2_000;
    private static final ObjectNode INPUT_SCHEMA = createInputSchema();
    private static final ToolMetadata METADATA = new ToolMetadata(
            "list_files",
            "List files and directories under a workspace directory without reading file contents.",
            INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.READ), ToolStatus.AVAILABLE);

    private final WorkspacePathResolver workspacePathResolver;

    public ListFilesTool() {
        this(new WorkspacePathResolver());
    }

    public ListFilesTool(WorkspacePathResolver workspacePathResolver) {
        this.workspacePathResolver = Objects.requireNonNull(workspacePathResolver, "workspacePathResolver");
    }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override
    public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input)
                .pathField("path", false)
                .optionalInteger("depth", 0, MAX_DEPTH)
                .optionalInteger("maxDepth", 0, MAX_DEPTH)
                .optionalInteger("limit", 1, MAX_LIMIT)
                .optionalBoolean("includeHidden")
                .custom((rawInput, builder) -> {
                    if (!builder.normalized().has("maxDepth") && builder.normalized().has("depth")) {
                        builder.normalized().put("maxDepth", builder.normalized().get("depth").asInt());
                    }
                    builder.normalized().remove("depth");
                })
                .build();
    }

    @Override
    public ToolResult run(JsonNode normalizedInput, ToolContext toolContext) {
        String inputPath = normalizedInput.has("path") ? normalizedInput.get("path").asText() : ".";
        int maxDepth = normalizedInput.has("maxDepth") ? normalizedInput.get("maxDepth").asInt() : DEFAULT_MAX_DEPTH;
        int limit = normalizedInput.has("limit") ? normalizedInput.get("limit").asInt() : DEFAULT_LIMIT;
        boolean includeHidden = normalizedInput.has("includeHidden") && normalizedInput.get("includeHidden").asBoolean();

        try {
            toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            WorkspacePathResult base = workspacePathResolver.resolve(
                    new WorkspacePathRequest(toolContext.cwd(), inputPath, PathIntent.LIST, true, true));
            toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);

            Listing listing = new Listing(limit);
            collect(toolContext.cwd(), base.resolvedPath().normalizedPath(), 0, maxDepth,
                    includeHidden, listing, toolContext);
            return ToolResult.ok(format(base.resolvedPath().normalizedPath(), listing));
        } catch (WorkspacePathException | IOException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private void collect(Path cwd, Path directory, int depth, int maxDepth, boolean includeHidden,
                         Listing listing, ToolContext toolContext) throws IOException {
        toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
        if (depth >= maxDepth || listing.truncated()) return;

        List<Path> children = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
                if (!includeHidden && isHiddenName(child)) continue;
                children.add(child);
            }
        }
        children.sort(Comparator.comparing(p -> directory.relativize(p).toString()));

        for (Path child : children) {
            toolContext.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            boolean isDir = Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS);
            if (!listing.add(directory.relativize(child).toString().replace('\\', '/') + (isDir ? "/" : ""))) return;
            if (isDir) collect(cwd, child, depth + 1, maxDepth, includeHidden, listing, toolContext);
        }
    }

    private static boolean isHiddenName(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().startsWith(".");
    }

    private static String format(Path base, Listing listing) {
        StringBuilder sb = new StringBuilder();
        sb.append("BASE: ").append(base).append('\n');
        sb.append("COUNT: ").append(listing.entries.size()).append('\n');
        sb.append("TRUNCATED: ").append(listing.truncated()).append('\n');
        listing.entries.forEach(e -> sb.append(e).append('\n'));
        return sb.toString();
    }

    private static ObjectNode createInputSchema() {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("path").put("type", "string").put("description", "Directory path to list.");
        props.putObject("depth").put("type", "integer").put("minimum", 0).put("maximum", MAX_DEPTH);
        props.putObject("maxDepth").put("type", "integer").put("minimum", 0).put("maximum", MAX_DEPTH);
        props.putObject("limit").put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT);
        props.putObject("includeHidden").put("type", "boolean");
        schema.set("required", JSON.arrayNode());
        return schema;
    }

    private static final class Listing {
        private final int limit;
        private final List<String> entries = new ArrayList<>();
        private boolean truncated;

        Listing(int limit) { this.limit = limit; }

        boolean add(String entry) {
            if (entries.size() >= limit) { truncated = true; return false; }
            entries.add(entry);
            return true;
        }

        List<String> entries() { return entries; }
        boolean truncated() { return truncated; }
    }
}
