package code4j.tools.builtin;

import code4j.tools.result.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages background command execution. Tasks are submitted with a command and cwd,
 * executed in a thread pool, and tracked with unique task IDs.
 */
public final class BackgroundTaskManager {
    private final ExecutorService executor;
    private final ConcurrentMap<String, TaskEntry> tasks = new ConcurrentHashMap<>();
    private final ToolResultStorage storage;
    private final int maxOutputChars;

    public BackgroundTaskManager(ToolResultStorage storage) {
        this(storage, Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "bg-task");
            t.setDaemon(true);
            return t;
        }), 100_000);
    }

    public BackgroundTaskManager(ToolResultStorage storage, ExecutorService executor, int maxOutputChars) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.maxOutputChars = maxOutputChars;
    }

    public BackgroundTaskResult submit(String command, List<String> args, Path cwd, java.time.Duration timeout) {
        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        TaskEntry entry = new TaskEntry(taskId, command, cwd, BackgroundTaskStatus.RUNNING, now, null, null, null);
        tasks.put(taskId, entry);

        executor.submit(() -> {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(command);
                cmd.addAll(args);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(cwd.toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                entry.pid.set(p.pid());

                boolean finished;
                if (timeout != null) {
                    finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    p.waitFor();
                    finished = true;
                }

                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!finished) {
                    p.destroyForcibly();
                    p.waitFor(1, TimeUnit.SECONDS);
                }
                int exit = finished ? p.exitValue() : -1;

                String content = "EXIT: " + exit + "\nSTDOUT:\n" +
                        (out.length() > maxOutputChars ? out.substring(0, maxOutputChars) + "\n... truncated" : out);
                ToolResultStorageRef ref = storage.store(content);

                entry.status.set(BackgroundTaskStatus.COMPLETED);
                entry.endedAt.set(Instant.now());
                entry.exitCode.set(exit);
                entry.outputRef.set(ref);
            } catch (IOException e) {
                entry.status.set(BackgroundTaskStatus.FAILED);
                entry.endedAt.set(Instant.now());
                try {
                    entry.outputRef.set(storage.store("ERROR: " + e.getMessage()));
                } catch (RuntimeException ignored) {}
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                entry.status.set(BackgroundTaskStatus.FAILED);
                entry.endedAt.set(Instant.now());
            }
        });

        return new BackgroundTaskResult(taskId, BackgroundTaskType.COMMAND, command, cwd.toString(),
                Optional.empty(), BackgroundTaskStatus.RUNNING, now, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    public Optional<BackgroundTaskResult> check(String taskId) {
        TaskEntry entry = tasks.get(taskId);
        if (entry == null) return Optional.empty();
        return Optional.of(entry.toResult());
    }

    public boolean cancel(String taskId) {
        TaskEntry entry = tasks.get(taskId);
        if (entry == null) return false;
        entry.status.set(BackgroundTaskStatus.CANCELLED);
        entry.endedAt.set(Instant.now());
        return true;
    }

    public List<BackgroundTaskResult> list() {
        return tasks.values().stream().map(TaskEntry::toResult).toList();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static class TaskEntry {
        final String taskId;
        final String command;
        final Path cwd;
        final AtomicReference<BackgroundTaskStatus> status;
        final Instant startedAt;
        final AtomicReference<Instant> endedAt;
        final AtomicReference<Integer> exitCode;
        final AtomicReference<ToolResultStorageRef> outputRef;
        final AtomicReference<Long> pid;

        TaskEntry(String taskId, String command, Path cwd, BackgroundTaskStatus status,
                  Instant startedAt, Instant endedAt, Integer exitCode, ToolResultStorageRef outputRef) {
            this.taskId = taskId;
            this.command = command;
            this.cwd = cwd;
            this.status = new AtomicReference<>(status);
            this.startedAt = startedAt;
            this.endedAt = new AtomicReference<>(endedAt);
            this.exitCode = new AtomicReference<>(exitCode);
            this.outputRef = new AtomicReference<>(outputRef);
            this.pid = new AtomicReference<>();
        }

        BackgroundTaskResult toResult() {
            BackgroundTaskStatus s = status.get();
            Instant end = endedAt.get();
            Integer ec = exitCode.get();
            ToolResultStorageRef ref = outputRef.get();
            Long p = pid.get();
            return new BackgroundTaskResult(taskId, BackgroundTaskType.COMMAND, command, cwd.toString(),
                    Optional.ofNullable(p), s, startedAt, Optional.ofNullable(end),
                    Optional.ofNullable(ec),
                    ref != null ? Optional.of(ref.id()) : Optional.empty(),
                    Optional.empty());
        }
    }
}
