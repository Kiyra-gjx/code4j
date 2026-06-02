package code4j.mcp;

import code4j.permissions.api.PermissionService;
import code4j.tools.api.Tool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class McpToolHydrator {
    private McpToolHydrator() {}

    public static McpRuntime hydrate(Map<String, McpServerConfig> configs, PermissionService ps, Path baseCwd) {
        List<Tool> tools = new ArrayList<>();
        List<McpServerSummary> summaries = new ArrayList<>();
        List<McpClient> clients = new ArrayList<>();
        Path bc = Objects.requireNonNull(baseCwd, "baseCwd").toAbsolutePath().normalize();
        for (var entry : Objects.requireNonNull(configs, "configs").entrySet()) {
            String name = entry.getKey();
            McpServerConfig config = entry.getValue();
            String cmd = config.endpointSummary();
            if (!config.enabled()) {
                summaries.add(new McpServerSummary(name, cmd, McpServerStatus.DISABLED, 0, Optional.empty()));
                continue;
            }
            StdioMcpClient client = new StdioMcpClient(name, config, bc);
            try {
                client.start();
                List<McpToolDescriptor> descriptors = client.listTools();
                for (McpToolDescriptor d : descriptors) {
                    tools.add(ps == null ? new McpBackedTool(name, d, client) : new McpBackedTool(name, d, client, ps));
                }
                clients.add(client);
                summaries.add(new McpServerSummary(name, cmd, McpServerStatus.CONNECTED, descriptors.size(), Optional.empty()));
            } catch (RuntimeException e) {
                client.close();
                McpErrorKind kind = e instanceof McpException me ? me.kind() : McpErrorKind.TOOL_CALL_FAILED;
                summaries.add(new McpServerSummary(name, cmd, McpServerStatus.ERROR, 0, Optional.of(msg(e)), Optional.of(kind)));
            }
        }
        return new McpRuntime(tools, summaries, clients);
    }

    private static String msg(RuntimeException e) { String m = e.getMessage(); return m == null || m.isBlank() ? e.getClass().getSimpleName() : m; }
}
