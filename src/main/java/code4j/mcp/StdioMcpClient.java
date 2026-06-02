package code4j.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class StdioMcpClient implements McpClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String serverName;
    private final McpServerConfig config;
    private final Path baseCwd;
    private Process process;
    private ProcessHandle lastProcessHandle;
    private int nextId = 1;

    public StdioMcpClient(String serverName, McpServerConfig config, Path baseCwd) {
        this.serverName = requireText(serverName, "serverName");
        this.config = Objects.requireNonNull(config, "config");
        this.baseCwd = Objects.requireNonNull(baseCwd, "baseCwd").toAbsolutePath().normalize();
    }

    @Override
    public synchronized void start() {
        if (process != null && process.isAlive()) return;
        spawnProcess();
        request("initialize", initParams(), config.initializeTimeout(), McpErrorKind.HANDSHAKE_FAILED);
        notify("notifications/initialized", MAPPER.createObjectNode());
    }

    @Override
    public synchronized List<McpToolDescriptor> listTools() {
        JsonNode result = request("tools/list", MAPPER.createObjectNode(), config.callTimeout(), McpErrorKind.LIST_TOOLS_FAILED);
        JsonNode tools = result.get("tools");
        if (tools == null || !tools.isArray()) return List.of();
        List<McpToolDescriptor> list = new ArrayList<>();
        for (JsonNode t : tools) {
            String name = t.path("name").asText("");
            if (name.isBlank()) continue;
            JsonNode schema = t.get("inputSchema");
            list.add(new McpToolDescriptor(name, t.path("description").asText(""),
                    schema == null || schema.isNull() ? Optional.empty() : Optional.of(schema)));
        }
        return List.copyOf(list);
    }

    @Override
    public synchronized JsonNode callTool(String name, JsonNode arguments) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments == null || arguments.isNull() ? MAPPER.createObjectNode() : arguments);
        return request("tools/call", params, config.callTimeout(), McpErrorKind.TOOL_CALL_FAILED);
    }

    @Override
    public synchronized void close() {
        if (process == null) return;
        Process p = process;
        try { p.getOutputStream().close(); p.getInputStream().close(); p.getErrorStream().close(); } catch (IOException ignored) {}
        try {
            if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroy();
            if (!p.waitFor(1, TimeUnit.SECONDS)) { p.destroyForcibly(); p.waitFor(1, TimeUnit.SECONDS); }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); p.destroyForcibly(); }
        finally { process = null; }
    }

    private void spawnProcess() {
        if (config.command().isBlank()) throw new McpException(McpErrorKind.START_FAILED, "No command for " + serverName);
        ProcessBuilder pb = new ProcessBuilder(config.command());
        pb.command().addAll(config.args());
        pb.directory(config.cwd().map(c -> baseCwd.resolve(c).toAbsolutePath().normalize()).orElse(baseCwd).toFile());
        pb.environment().putAll(config.env());
        try { process = pb.start(); lastProcessHandle = process.toHandle(); }
        catch (IOException e) { throw new McpException(McpErrorKind.START_FAILED, "Failed to start " + serverName + ": " + config.command(), e); }
    }

    private JsonNode request(String method, JsonNode params, java.time.Duration timeout, McpErrorKind failKind) {
        ensureRunning();
        int id = nextId++;
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0"); msg.put("id", id); msg.put("method", method); msg.set("params", params);
        writeMessage(msg);
        JsonNode resp = readWithTimeout(timeout, method, failKind);
        if (resp.has("error")) throw new McpException(failKind, serverName + ": " + resp.path("error").path("message").asText("request failed"));
        return resp.path("result");
    }

    private void notify(String method, JsonNode params) {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0"); msg.put("method", method); msg.set("params", params);
        writeMessage(msg);
    }

    private ObjectNode initParams() {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("protocolVersion", "2024-11-05"); p.putObject("capabilities");
        p.putObject("clientInfo").put("name", "code4j").put("version", "0.1.0");
        return p;
    }

    private void writeMessage(JsonNode msg) {
        ensureRunning();
        try {
            byte[] body = MAPPER.writeValueAsBytes(msg);
            OutputStream stdin = process.getOutputStream();
            stdin.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            stdin.write(body); stdin.flush();
        } catch (IOException e) { throw new McpException(McpErrorKind.PROCESS_EXITED, serverName + " closed during write", e); }
    }

    private JsonNode readWithTimeout(java.time.Duration timeout, String method, McpErrorKind failKind) {
        CompletableFuture<JsonNode> f = CompletableFuture.supplyAsync(() -> readMessage(method, failKind));
        try { return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { throw new McpException(McpErrorKind.TIMEOUT, serverName + ": timeout for " + method, e); }
        catch (McpException e) { throw e; }
        catch (Exception e) { Throwable c = e.getCause() == null ? e : e.getCause(); if (c instanceof McpException me) throw me; throw new McpException(failKind, serverName + ": failed for " + method, c); }
    }

    private JsonNode readMessage(String method, McpErrorKind failKind) {
        try {
            InputStream stdout = process.getInputStream();
            byte[] sep = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            int matched = 0;
            while (matched < sep.length) {
                int next = stdout.read();
                if (next < 0) throw new McpException(McpErrorKind.PROCESS_EXITED, serverName + " closed before " + method);
                header.write(next);
                matched = next == sep[matched] ? matched + 1 : next == sep[0] ? 1 : 0;
            }
            int cl = contentLength(header.toString(StandardCharsets.US_ASCII));
            if (cl <= 0) throw new McpException(McpErrorKind.PROTOCOL_ERROR, serverName + ": missing Content-Length for " + method);
            byte[] body = stdout.readNBytes(cl);
            if (body.length < cl) throw new McpException(McpErrorKind.PROCESS_EXITED, serverName + " closed during " + method + " response");
            return MAPPER.readTree(body);
        } catch (McpException e) { throw e; }
        catch (IOException e) { throw new McpException(McpErrorKind.PROTOCOL_ERROR, serverName + ": invalid response for " + method, e); }
        catch (RuntimeException e) { throw new McpException(failKind, serverName + ": failed to read " + method, e); }
    }

    private int contentLength(String header) {
        for (String line : header.split("\\r\\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) return Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
        }
        return -1;
    }

    private void ensureRunning() { if (process == null || !process.isAlive()) throw new McpException(McpErrorKind.PROCESS_EXITED, serverName + " is not running"); }

    private static String requireText(String v, String n) { if (Objects.requireNonNull(v, n).isBlank()) throw new IllegalArgumentException(n + " must not be blank"); return v; }
}
