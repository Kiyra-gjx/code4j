package code4j.tools.builtin;

import code4j.tools.api.ToolContext;
import code4j.workspace.ResolvedWorkspacePath;
import code4j.workspace.WorkspaceBoundary;

/**
 * Controls whether a tool is allowed to read a given path.
 * Inside the workspace, reads are always allowed. Outside the workspace,
 * permission is required (hooked into the permissions module later).
 */
@FunctionalInterface
public interface ReadFilePathAccess {
    void ensureReadAllowed(ToolContext toolContext, ResolvedWorkspacePath resolvedPath);

    /** A no-op access checker — always allows reads. Permission system will be wired in later. */
    static ReadFilePathAccess alwaysAllow() {
        return (toolContext, resolvedPath) -> {
            // All reads allowed for now — permissions module will add checks later
        };
    }

    /** Only allows reads inside the workspace boundary. */
    static ReadFilePathAccess workspaceOnly() {
        return (toolContext, resolvedPath) -> {
            if (resolvedPath.boundary() == WorkspaceBoundary.OUTSIDE_CWD) {
                throw new SecurityException("Read access denied outside workspace: " + resolvedPath.rawPath());
            }
        };
    }
}
