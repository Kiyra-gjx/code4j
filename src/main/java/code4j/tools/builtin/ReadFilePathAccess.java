package code4j.tools.builtin;

import code4j.permissions.api.PermissionService;
import code4j.permissions.model.PathIntent;
import code4j.permissions.model.PermissionContext;
import code4j.tools.api.ToolContext;
import code4j.workspace.ResolvedWorkspacePath;
import code4j.workspace.WorkspaceBoundary;

/**
 * Controls whether a tool is allowed to read a given path.
 */
@FunctionalInterface
public interface ReadFilePathAccess {
    void ensureReadAllowed(ToolContext toolContext, ResolvedWorkspacePath resolvedPath);

    static ReadFilePathAccess alwaysAllow() {
        return (toolContext, resolvedPath) -> {};
    }

    static ReadFilePathAccess workspaceOnly() {
        return (toolContext, resolvedPath) -> {
            if (resolvedPath.boundary() == WorkspaceBoundary.OUTSIDE_CWD) {
                throw new SecurityException("Read access denied outside workspace: " + resolvedPath.rawPath());
            }
        };
    }

    static ReadFilePathAccess permissionBacked(PermissionService permissionService) {
        return (toolContext, resolvedPath) -> {
            PermissionContext ctx = new PermissionContext(
                    toolContext.sessionId(), toolContext.turnId(), toolContext.toolUseId());
            permissionService.ensurePath(resolvedPath.normalizedPath(), PathIntent.READ, ctx);
        };
    }
}
