package code4j.permissions.store;

import code4j.permissions.model.PermissionResource;

import java.nio.file.Path;
import java.util.Objects;

public record PermissionResourceKey(String type, String fingerprint) {
    public PermissionResourceKey {
        if (Objects.requireNonNull(type, "type").isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (Objects.requireNonNull(fingerprint, "fingerprint").isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
    }

    public static PermissionResourceKey from(PermissionResource resource) {
        return switch (resource) {
            case PermissionResource.PathResource p -> new PermissionResourceKey("path",
                    p.intent() + "|" + p.path().toAbsolutePath().normalize());
            case PermissionResource.CommandResource c -> new PermissionResourceKey("command",
                    c.classification() + "|" + c.signature().executable() + "|"
                            + String.join("", c.signature().arguments()));
            case PermissionResource.EditResource e -> new PermissionResourceKey("edit",
                    "EDIT|" + e.path().toAbsolutePath().normalize());
            case PermissionResource.McpToolResource m -> new PermissionResourceKey("mcp_tool",
                    m.serverName() + "|" + m.toolName() + "|" + m.wrappedName());
        };
    }
}
