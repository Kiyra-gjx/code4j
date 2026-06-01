package code4j.tools.registry;

import code4j.core.turn.CancellationPhase;
import code4j.core.turn.CancellationRequestedException;
import code4j.tools.api.*;
import code4j.tools.result.ToolResult;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Central registry for all tools. Implements {@link ToolExecutor} so the agent
 * loop can call {@link #execute(ToolCall, ToolContext)} without knowing about
 * individual tool implementations.
 * <p>
 * The execution pipeline for each tool call is:
 * <ol>
 *   <li>Check cancellation token</li>
 *   <li>Look up the tool by name</li>
 *   <li>Validate and normalize the input</li>
 *   <li>Run the tool with normalized input</li>
 *   <li>Check cancellation token again (tool may have been slow)</li>
 * </ol>
 * If any step fails, a {@link ToolResult#error(String)} is returned —
 * the agent loop never sees an exception from tool execution.
 */
public final class ToolRegistry implements ToolExecutor {
    private final Map<String, Tool> toolsByName = new LinkedHashMap<>();

    public void register(Tool tool) {
        Tool actualTool = Objects.requireNonNull(tool, "tool");
        String name = actualTool.metadata().name();
        if (toolsByName.containsKey(name)) {
            throw new IllegalArgumentException("Tool already registered: " + name);
        }
        toolsByName.put(name, actualTool);
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(toolsByName.get(Objects.requireNonNull(name, "name")));
    }

    public List<Tool> list() {
        return List.copyOf(toolsByName.values());
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext toolContext) {
        ToolCall actualCall = Objects.requireNonNull(call, "call");
        ToolContext ctx = Objects.requireNonNull(toolContext, "toolContext");

        ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);

        Tool tool = toolsByName.get(actualCall.toolName());
        if (tool == null) {
            return ToolResult.error("Unknown tool: " + actualCall.toolName());
        }

        ValidationResult validation;
        try {
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            validation = tool.validateInput(actualCall.input());
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
        } catch (CancellationRequestedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ToolResult.error(messageOrDefault(exception, "Tool input validation failed"));
        }

        if (validation == null) {
            return ToolResult.error("Tool input validation failed: validator returned null");
        }
        if (!validation.valid()) {
            return ToolResult.error(formatValidationErrors(actualCall.toolName(), validation));
        }
        if (validation.normalizedInput().isEmpty()) {
            return ToolResult.error("Tool input validation failed: valid result requires normalized input");
        }

        JsonNode normalizedInput = validation.normalizedInput().get();

        try {
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            ToolResult result = tool.run(normalizedInput, ctx);
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);
            return result == null ? ToolResult.error("Tool returned null ToolResult") : result;
        } catch (CancellationRequestedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ToolResult.error(messageOrDefault(exception, "Tool execution failed"));
        }
    }

    private static String formatValidationErrors(String toolName, ValidationResult validation) {
        StringJoiner joiner = new StringJoiner("; ");
        validation.errors().forEach(joiner::add);
        return "Tool input validation failed for " + toolName + ": " + joiner;
    }

    private static String messageOrDefault(RuntimeException exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
