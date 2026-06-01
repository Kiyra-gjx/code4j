package code4j.tools.api;

import code4j.tools.result.ToolResult;

import java.util.Objects;

/**
 * Executes a tool call. The primary implementation is {@link code4j.tools.registry.ToolRegistry}.
 */
@FunctionalInterface
public interface ToolExecutor {
    ToolResult execute(ToolCall call, ToolContext toolContext);

    static ToolExecutor unsupported() {
        return (call, toolContext) -> {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(toolContext, "toolContext");
            throw new IllegalArgumentException("Unknown tool: " + call.toolName());
        };
    }
}
