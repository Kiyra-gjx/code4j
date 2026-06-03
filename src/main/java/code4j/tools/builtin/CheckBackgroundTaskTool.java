package code4j.tools.builtin;

import code4j.tools.api.Tool;
import code4j.tools.api.ToolContext;
import code4j.tools.api.ValidationResult;
import code4j.tools.metadata.ToolCapability;
import code4j.tools.metadata.ToolMetadata;
import code4j.tools.metadata.ToolOrigin;
import code4j.tools.metadata.ToolStatus;
import code4j.tools.result.BackgroundTaskResult;
import code4j.tools.result.BackgroundTaskStatus;
import code4j.tools.result.ToolResult;
import code4j.tools.validation.ToolInputValidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CheckBackgroundTaskTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createSchema();
    private static final ToolMetadata METADATA = new ToolMetadata(
            "check_background_task",
            "Check status, cancel, or list background tasks started with run_command background=true.",
            INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.COMMAND), ToolStatus.AVAILABLE);

    private final BackgroundTaskManager taskManager;

    public CheckBackgroundTaskTool(BackgroundTaskManager taskManager) {
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
    }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override
    public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input)
                .custom((raw, b) -> {
                    JsonNode action = raw != null && raw.isObject() ? raw.get("action") : null;
                    String actionStr = action != null && action.isTextual() ? action.asText().trim() : "status";
                    if (!Set.of("status", "cancel", "list").contains(actionStr)) {
                        b.addError("action must be one of: status, cancel, list");
                        return;
                    }
                    b.normalized().put("action", actionStr);
                    if (!"list".equals(actionStr)) {
                        JsonNode taskId = raw != null && raw.isObject() ? raw.get("taskId") : null;
                        if (taskId == null || !taskId.isTextual() || taskId.asText().isBlank()) {
                            b.addError("taskId is required for status and cancel actions");
                        } else {
                            b.normalized().put("taskId", taskId.asText().trim());
                        }
                    }
                })
                .build();
    }

    @Override
    public ToolResult run(JsonNode normalizedInput, ToolContext toolContext) {
        String action = normalizedInput.get("action").asText();
        return switch (action) {
            case "status" -> handleStatus(normalizedInput.get("taskId").asText());
            case "cancel" -> handleCancel(normalizedInput.get("taskId").asText());
            case "list" -> handleList();
            default -> ToolResult.error("Unknown action: " + action);
        };
    }

    private ToolResult handleStatus(String taskId) {
        Optional<BackgroundTaskResult> result = taskManager.check(taskId);
        if (result.isEmpty()) return ToolResult.error("Task not found: " + taskId);
        return ToolResult.ok(formatTask(result.get()));
    }

    private ToolResult handleCancel(String taskId) {
        boolean cancelled = taskManager.cancel(taskId);
        if (!cancelled) return ToolResult.error("Task not found: " + taskId);
        return ToolResult.ok("Task cancelled: " + taskId);
    }

    private ToolResult handleList() {
        List<BackgroundTaskResult> tasks = taskManager.list();
        if (tasks.isEmpty()) return ToolResult.ok("No background tasks.");
        StringBuilder sb = new StringBuilder("Background tasks:\n");
        for (BackgroundTaskResult t : tasks) {
            sb.append(formatTask(t)).append("\n");
        }
        return ToolResult.ok(sb.toString().trim());
    }

    private static String formatTask(BackgroundTaskResult t) {
        return String.format("[%s] %s %s (status=%s, pid=%s, started=%s, ended=%s, exit=%s)",
                t.taskId(), t.command(), t.cwd(), t.status(),
                t.pid().map(Object::toString).orElse("?"), t.startedAt(),
                t.endedAt().map(Instant::toString).orElse("-"),
                t.exitCode().map(Object::toString).orElse("-"));
    }

    private static ObjectNode createSchema() {
        ObjectNode s = JSON.objectNode();
        s.put("type", "object");
        var p = s.putObject("properties");
        p.putObject("action").put("type", "string")
                .put("description", "Action: status, cancel, or list (default: status).");
        p.putObject("taskId").put("type", "string")
                .put("description", "Task ID returned by run_command with background=true.");
        return s;
    }
}
