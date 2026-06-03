package code4j.tools.builtin;

import code4j.core.turn.CancellationPhase;
import code4j.permissions.api.PermissionService;
import code4j.permissions.model.*;
import code4j.tools.api.Tool;
import code4j.tools.api.ToolContext;
import code4j.tools.api.ValidationResult;
import code4j.tools.metadata.*;
import code4j.tools.result.BackgroundTaskResult;
import code4j.tools.result.BackgroundTaskStatus;
import code4j.tools.result.BackgroundTaskType;
import code4j.tools.result.ToolResult;
import code4j.tools.validation.ToolInputValidation;
import code4j.workspace.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class RunCommandTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createSchema();
    private static final ToolMetadata METADATA = new ToolMetadata("run_command", "Execute a shell command in the workspace.", INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.COMMAND), ToolStatus.AVAILABLE);
    private static final int MAX_OUTPUT_CHARS = 50_000;

    private final WorkspacePathResolver pathResolver;
    private final CommandClassifier classifier;
    private final CommandTimeoutPolicy timeoutPolicy;
    private final PermissionService permissionService;
    private final BackgroundTaskManager backgroundTaskManager;

    public RunCommandTool(WorkspacePathResolver pathResolver, PermissionService permissionService) {
        this(pathResolver, new CommandClassifier(), new CommandTimeoutPolicy(), permissionService, null);
    }

    public RunCommandTool(WorkspacePathResolver pathResolver, CommandClassifier classifier, CommandTimeoutPolicy timeoutPolicy, PermissionService permissionService) {
        this(pathResolver, classifier, timeoutPolicy, permissionService, null);
    }

    public RunCommandTool(WorkspacePathResolver pathResolver, PermissionService permissionService, BackgroundTaskManager backgroundTaskManager) {
        this(pathResolver, new CommandClassifier(), new CommandTimeoutPolicy(), permissionService, backgroundTaskManager);
    }

    RunCommandTool(WorkspacePathResolver pathResolver, CommandClassifier classifier, CommandTimeoutPolicy timeoutPolicy, PermissionService permissionService, BackgroundTaskManager backgroundTaskManager) {
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.timeoutPolicy = Objects.requireNonNull(timeoutPolicy, "timeoutPolicy");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
        this.backgroundTaskManager = backgroundTaskManager;
    }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input).requiredString("command").optionalStringArray("args", true)
                .optionalInteger("timeout", 1, timeoutPolicy.maxTimeoutSeconds()).optionalBoolean("background").cwdField("cwd", false).build();
    }

    @Override public ToolResult run(JsonNode input, ToolContext ctx) {
        if (input.has("background") && input.get("background").asBoolean()) {
            if (backgroundTaskManager == null) {
                return ToolResult.error("Background tasks not available. No BackgroundTaskManager configured.");
            }
            String command = input.get("command").asText();
            List<String> args2 = new ArrayList<>();
            if (input.has("args")) for (JsonNode a : input.get("args")) args2.add(a.asText());
            Path cwd2;
            try {
                cwd2 = input.has("cwd") ? pathResolver.resolve(new WorkspacePathRequest(ctx.cwd(), input.get("cwd").asText(), PathIntent.COMMAND_CWD, true, true)).resolvedPath().normalizedPath() : ctx.cwd();
            } catch (WorkspacePathException e) { return ToolResult.error(e.getMessage()); }
            CommandClassificationResult cr2 = classifier.classify(command, args2);
            permissionService.ensureCommand(
                    new CommandSignature(command, args2), cr2.classification(),
                    new PermissionContext(ctx.sessionId(), ctx.turnId(), ctx.toolUseId()));
            Duration timeout2 = timeoutPolicy.timeoutFor(input.has("timeout") ? input.get("timeout").asInt() : null);
            BackgroundTaskResult btr = backgroundTaskManager.submit(command, args2, cwd2, timeout2);
            return ToolResult.ok("BACKGROUND TASK STARTED\nTASK_ID: " + btr.taskId()
                    + "\nCOMMAND: " + command + "\nCWD: " + cwd2 + "\nSTATUS: " + btr.status()
                    + "\n\nUse check_background_task with action=status and taskId=" + btr.taskId() + " to check progress.");
        }

        String command = input.get("command").asText();
        List<String> args = new ArrayList<>();
        if (input.has("args")) for (JsonNode a : input.get("args")) args.add(a.asText());
        Duration timeout = timeoutPolicy.timeoutFor(input.has("timeout") ? input.get("timeout").asInt() : null);

        CommandClassificationResult cr = classifier.classify(command, args);
        permissionService.ensureCommand(
                new CommandSignature(command, args), cr.classification(),
                new PermissionContext(ctx.sessionId(), ctx.turnId(), ctx.toolUseId()));

        try {
            Path cwd = input.has("cwd") ? pathResolver.resolve(new WorkspacePathRequest(ctx.cwd(), input.get("cwd").asText(), PathIntent.COMMAND_CWD, true, true)).resolvedPath().normalizedPath() : ctx.cwd();
            ctx.cancellationToken().throwIfCancellationRequested(CancellationPhase.TOOL_EXECUTION);

            List<String> cmd = new ArrayList<>(); cmd.add(command); cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd); pb.directory(cwd.toFile());
            Process p = pb.start();

            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) { p.destroyForcibly(); p.waitFor(1, TimeUnit.SECONDS); return ToolResult.error("Command timed out after " + timeout.toSeconds() + "s: " + command); }

            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = p.exitValue();
            StringBuilder sb = new StringBuilder();
            sb.append("EXIT: ").append(exit).append("\n");
            if (!out.isEmpty()) { sb.append("STDOUT:\n"); sb.append(out.length() > MAX_OUTPUT_CHARS ? out.substring(0, MAX_OUTPUT_CHARS) + "\n... truncated" : out); sb.append("\n"); }
            if (!err.isEmpty()) { sb.append("STDERR:\n"); sb.append(err.length() > MAX_OUTPUT_CHARS ? err.substring(0, MAX_OUTPUT_CHARS) + "\n... truncated" : err); }
            return exit == 0 ? ToolResult.ok(sb.toString()) : ToolResult.error(sb.toString());
        } catch (WorkspacePathException e) { return ToolResult.error(e.getMessage()); }
        catch (IOException e) { return ToolResult.error("Command failed: " + e.getMessage()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return ToolResult.error("Command interrupted"); }
    }

    private static ObjectNode createSchema() { ObjectNode s = JSON.objectNode(); s.put("type", "object"); var p = s.putObject("properties"); p.putObject("command").put("type", "string"); var a = p.putObject("args"); a.put("type", "array"); a.putObject("items").put("type", "string"); p.putObject("timeout").put("type", "integer").put("minimum", 1); p.putObject("cwd").put("type", "string"); var r = s.putArray("required"); r.add("command"); return s; }
}
