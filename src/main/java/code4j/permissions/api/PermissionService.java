package code4j.permissions.api;

import code4j.permissions.model.*;

import java.nio.file.Path;

public interface PermissionService {
    PermissionGrant ensurePath(Path path, PathIntent intent, PermissionContext context);
    PermissionGrant ensureCommand(CommandSignature signature, CommandClassification classification, PermissionContext context);
    PermissionGrant ensureEdit(PermissionResource.EditResource resource, PermissionContext context);
    PermissionGrant ensureMcpTool(PermissionResource.McpToolResource resource, PermissionContext context);
    default void beginTurn(String turnId) {}
    default void endTurn(String turnId) {}
}
