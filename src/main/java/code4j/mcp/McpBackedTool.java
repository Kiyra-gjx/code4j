package code4j.mcp;

import code4j.permissions.model.PermissionContext;
import code4j.permissions.model.PermissionDeniedException;
import code4j.permissions.model.PermissionResource;
import code4j.permissions.api.PermissionService;
import code4j.tools.api.Tool;
import code4j.tools.api.ToolContext;
import code4j.tools.api.ValidationResult;
import code4j.tools.metadata.ToolCapability;
import code4j.tools.metadata.ToolMetadata;
import code4j.tools.metadata.ToolOrigin;
import code4j.tools.metadata.ToolStatus;
import code4j.tools.result.ToolResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class McpBackedTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String serverName;
    private final McpToolDescriptor descriptor;
    private final McpClient client;
    private final Optional<PermissionService> permissionService;
    private final JsonNode inputSchema;
    private final ToolMetadata metadata;

    public McpBackedTool(String serverName, McpToolDescriptor descriptor, McpClient client) {
        this(serverName, descriptor, client, Optional.empty());
    }

    public McpBackedTool(String serverName, McpToolDescriptor descriptor, McpClient client, PermissionService ps) {
        this(serverName, descriptor, client, Optional.of(ps));
    }

    private McpBackedTool(String serverName, McpToolDescriptor descriptor, McpClient client,
                          Optional<PermissionService> permissionService) {
        this.serverName = requireText(serverName, "serverName");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.client = Objects.requireNonNull(client, "client");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
        this.inputSchema = descriptor.inputSchema().filter(JsonNode::isObject).map(JsonNode.class::cast)
                .orElseGet(() -> { ObjectNode f = MAPPER.createObjectNode(); f.put("type", "object"); f.put("additionalProperties", true); return f; });
        this.metadata = new ToolMetadata(McpToolName.wrappedName(serverName, descriptor.name()),
                descriptor.description().isBlank() ? "MCP tool " + descriptor.name() + " from " + serverName : descriptor.description(),
                inputSchema, ToolOrigin.MCP, Set.of(ToolCapability.COMMAND), ToolStatus.AVAILABLE);
    }

    @Override public ToolMetadata metadata() { return metadata; }
    @Override public JsonNode inputSchema() { return inputSchema; }

    @Override
    public ValidationResult validateInput(JsonNode input) {
        if (input == null || input.isNull() || input.isMissingNode()) return ValidationResult.valid(MAPPER.createObjectNode());
        if (!input.isObject()) return ValidationResult.invalid(List.of("MCP tool input must be a JSON object"));
        return ValidationResult.valid(input);
    }

    @Override
    public ToolResult run(JsonNode normalizedInput, ToolContext toolContext) {
        if (permissionService.isPresent()) {
            try {
                permissionService.orElseThrow().ensureMcpTool(new PermissionResource.McpToolResource(
                        serverName, descriptor.name(), metadata.name(), metadata.description()),
                        new PermissionContext(toolContext.sessionId(), toolContext.turnId(), toolContext.toolUseId()));
            } catch (PermissionDeniedException e) {
                return ToolResult.error(e.feedback().map(f -> "Permission denied: " + f).orElse("Permission denied"));
            }
        }
        return McpToolResultFormatter.toToolResult(client.callTool(descriptor.name(), normalizedInput));
    }

    private static String requireText(String v, String n) { if (Objects.requireNonNull(v, n).isBlank()) throw new IllegalArgumentException(n + " must not be blank"); return v; }
}
