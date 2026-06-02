package code4j.model.anthropic;

import code4j.config.RuntimeConfig;
import code4j.core.loop.ModelAdapter;
import code4j.core.message.*;
import code4j.core.step.*;
import code4j.model.ModelLimits;
import code4j.model.ProviderThinkingBlock;
import code4j.model.ProviderUsage;
import code4j.model.StepDiagnostics;
import code4j.tools.api.Tool;
import code4j.tools.api.ToolCall;
import code4j.tools.registry.ToolRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * Adapts the Anthropic Messages API to the {@link ModelAdapter} interface.
 * <p>
 * Converts internal ChatMessage objects into Anthropic-format JSON, sends the
 * HTTP request, and parses the response back into an {@link AgentStep}.
 */
public final class AnthropicModelAdapter implements ModelAdapter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long BASE_RETRY_DELAY_MS = 500L;
    private static final long MAX_RETRY_DELAY_MS = 8_000L;

    private final RuntimeConfig runtimeConfig;
    private final ToolRegistry tools;
    private final AnthropicTransport transport;
    private final Optional<Integer> resolvedMaxOutputTokens;
    private final int maxRetries;
    private final RetryDelayStrategy retryDelayStrategy;

    @FunctionalInterface
    public interface RetryDelayStrategy {
        void sleep(long millis);

        static RetryDelayStrategy threadSleep() {
            return millis -> {
                try {
                    Thread.sleep(Math.max(0L, millis));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ProviderRequestException("Retry sleep interrupted",
                            Optional.empty(), true, e);
                }
            };
        }
    }

    public AnthropicModelAdapter(RuntimeConfig runtimeConfig, ToolRegistry tools) {
        this(runtimeConfig, tools, new HttpAnthropicTransport());
    }

    public AnthropicModelAdapter(RuntimeConfig runtimeConfig, ToolRegistry tools, AnthropicTransport transport) {
        this(runtimeConfig, tools, transport, Optional.empty(), 2, RetryDelayStrategy.threadSleep());
    }

    public AnthropicModelAdapter(RuntimeConfig runtimeConfig, ToolRegistry tools, AnthropicTransport transport,
                                 Optional<Integer> resolvedMaxOutputTokens, int maxRetries,
                                 RetryDelayStrategy retryDelayStrategy) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.resolvedMaxOutputTokens = Objects.requireNonNull(resolvedMaxOutputTokens, "resolvedMaxOutputTokens")
                .filter(v -> v > 0);
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be non-negative");
        this.maxRetries = maxRetries;
        this.retryDelayStrategy = Objects.requireNonNull(retryDelayStrategy, "retryDelayStrategy");
    }

    @Override
    public AgentStep next(List<ChatMessage> messages) {
        JsonNode requestBody = requestBody(messages);
        AnthropicTransport.Response response = sendWithRetries(requestBody);
        JsonNode data = parseBody(response.body());
        if (!response.ok()) {
            throw new ProviderRequestException(extractErrorMessage(data, response.statusCode()),
                    Optional.of(response.statusCode()), shouldRetryStatus(response.statusCode()));
        }
        return parseStep(data);
    }

    private AnthropicTransport.Response sendWithRetries(JsonNode requestBody) {
        String url = runtimeConfig.baseUrl().replaceAll("/+$", "") + "/v1/messages";
        Map<String, String> h = headers();
        ProviderRequestException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                AnthropicTransport.Response response = transport.post(url, h, requestBody);
                if (response.ok() || !shouldRetryStatus(response.statusCode()) || attempt >= maxRetries) {
                    return response;
                }
                retryDelayStrategy.sleep(retryDelayMs(response, attempt + 1));
            } catch (ProviderRequestException e) {
                last = e;
                if (!e.retryable() || attempt >= maxRetries) throw e;
                retryDelayStrategy.sleep(retryDelayMs(null, attempt + 1));
            }
        }
        if (last != null) throw last;
        throw new ProviderRequestException("Provider request failed before receiving a response");
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("content-type", "application/json");
        h.put("anthropic-version", "2023-06-01");
        runtimeConfig.authToken().ifPresent(t -> h.put("Authorization", "Bearer " + t));
        if (runtimeConfig.authToken().isEmpty()) {
            runtimeConfig.apiKey().ifPresent(k -> h.put("x-api-key", k));
        }
        return h;
    }

    private JsonNode requestBody(List<ChatMessage> messages) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", runtimeConfig.model());
        root.put("system", systemText(messages));
        root.set("messages", toProviderMessages(messages));
        root.set("tools", toolSchemas());
        root.put("max_tokens", resolvedMaxOutputTokens.orElseGet(() ->
                ModelLimits.resolveMaxOutputTokens(runtimeConfig.model(), runtimeConfig.maxOutputTokens())));
        return root;
    }

    private String systemText(List<ChatMessage> messages) {
        return messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(m -> ((SystemMessage) m).content())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private ArrayNode toProviderMessages(List<ChatMessage> messages) {
        ArrayNode converted = MAPPER.createArrayNode();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) continue;
            if (message instanceof UserMessage u) pushBlock(converted, "user", textBlock(u.content()));
            else if (message instanceof ContextSummaryMessage s)
                pushBlock(converted, "user", textBlock("[Context Summary]\n" + s.content()));
            else if (message instanceof AssistantThinkingMessage t) {
                for (ProviderThinkingBlock b : t.blocks()) pushBlock(converted, "assistant", b.raw());
            } else if (message instanceof AssistantProgressMessage p)
                pushBlock(converted, "assistant", textBlock("<progress>\n" + p.content() + "\n</progress>"));
            else if (message instanceof AssistantMessage a)
                pushBlock(converted, "assistant", textBlock(a.content()));
            else if (message instanceof AssistantToolCallMessage tc) {
                ObjectNode block = MAPPER.createObjectNode();
                block.put("type", "tool_use");
                block.put("id", tc.toolUseId());
                block.put("name", tc.toolName());
                block.set("input", tc.input());
                pushBlock(converted, "assistant", block);
            } else if (message instanceof ToolResultMessage tr) {
                ObjectNode block = MAPPER.createObjectNode();
                block.put("type", "tool_result");
                block.put("tool_use_id", tr.toolUseId());
                block.put("content", tr.content());
                block.put("is_error", tr.error());
                pushBlock(converted, "user", block);
            }
        }
        return converted;
    }

    private void pushBlock(ArrayNode messages, String role, JsonNode block) {
        if (!messages.isEmpty() && role.equals(messages.get(messages.size() - 1).get("role").asText())) {
            ((ArrayNode) messages.get(messages.size() - 1).get("content")).add(block);
            return;
        }
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("role", role);
        msg.set("content", MAPPER.createArrayNode().add(block));
        messages.add(msg);
    }

    private ObjectNode textBlock(String text) {
        ObjectNode b = MAPPER.createObjectNode();
        b.put("type", "text");
        b.put("text", text);
        return b;
    }

    private ArrayNode toolSchemas() {
        ArrayNode schemas = MAPPER.createArrayNode();
        for (Tool tool : tools.list()) {
            ObjectNode s = MAPPER.createObjectNode();
            s.put("name", tool.metadata().name());
            s.put("description", tool.metadata().description());
            s.set("input_schema", tool.inputSchema());
            schemas.add(s);
        }
        return schemas;
    }

    private JsonNode parseBody(String body) {
        if (body == null || body.isBlank()) return MAPPER.createObjectNode();
        try { return MAPPER.readTree(body); }
        catch (Exception e) {
            ObjectNode fallback = MAPPER.createObjectNode();
            fallback.putObject("error").put("message", body.trim());
            return fallback;
        }
    }

    private AgentStep parseStep(JsonNode data) {
        JsonNode content = data.get("content");
        List<ToolCall> toolCalls = new ArrayList<>();
        List<String> textParts = new ArrayList<>();
        List<ProviderThinkingBlock> thinkingBlocks = new ArrayList<>();
        List<String> blockTypes = new ArrayList<>();
        LinkedHashSet<String> ignoredTypes = new LinkedHashSet<>();

        if (content != null && content.isArray()) {
            for (JsonNode block : content) {
                String type = block.path("type").asText("");
                blockTypes.add(type);
                switch (type) {
                    case "text" -> textParts.add(block.path("text").asText(""));
                    case "tool_use" -> toolCalls.add(new ToolCall(
                            block.path("id").asText(),
                            block.path("name").asText(),
                            block.path("input").isMissingNode() ? MAPPER.createObjectNode() : block.path("input")));
                    case "thinking", "redacted_thinking" ->
                            thinkingBlocks.add(new ProviderThinkingBlock(type, block));
                    default -> ignoredTypes.add(type);
                }
            }
        }

        ParsedText parsed = parseAssistantText(String.join("\n", textParts).trim());
        StepDiagnostics diagnostics = new StepDiagnostics(
                optText(data.path("stop_reason").asText("")), blockTypes, List.copyOf(ignoredTypes));
        Optional<ProviderUsage> usage = normalizeUsage(data.get("usage"));

        if (!toolCalls.isEmpty()) {
            return new ToolCallsStep(toolCalls, optText(parsed.content()),
                    parsed.kind() == AssistantKind.PROGRESS ? ContentKind.PROGRESS : ContentKind.UNSPECIFIED,
                    thinkingBlocks, Optional.of(diagnostics), usage);
        }
        return new AssistantStep(parsed.content(), parsed.kind(), thinkingBlocks, Optional.of(diagnostics), usage);
    }

    private ParsedText parseAssistantText(String raw) {
        String t = raw.trim();
        if (t.startsWith("<final>")) return new ParsedText(
                t.substring("<final>".length()).replaceAll("(?i)</final>", "").trim(), AssistantKind.FINAL);
        if (t.startsWith("[FINAL]")) return new ParsedText(
                t.substring("[FINAL]".length()).trim(), AssistantKind.FINAL);
        if (t.startsWith("<progress>")) return new ParsedText(
                t.substring("<progress>".length()).replaceAll("(?i)</progress>", "").trim(), AssistantKind.PROGRESS);
        if (t.startsWith("[PROGRESS]")) return new ParsedText(
                t.substring("[PROGRESS]".length()).trim(), AssistantKind.PROGRESS);
        return new ParsedText(t, AssistantKind.UNSPECIFIED);
    }

    private Optional<ProviderUsage> normalizeUsage(JsonNode usage) {
        if (usage == null || usage.isNull()) return Optional.empty();
        int input = usage.path("input_tokens").asInt(0)
                + usage.path("cache_creation_input_tokens").asInt(0)
                + usage.path("cache_read_input_tokens").asInt(0);
        int output = usage.path("output_tokens").asInt(0);
        int total = input + output;
        return total <= 0 ? Optional.empty() : Optional.of(new ProviderUsage(input, output, total));
    }

    private Optional<String> optText(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String extractErrorMessage(JsonNode data, int status) {
        String nested = data.path("error").path("message").asText("");
        if (!nested.isBlank()) return nested;
        String error = data.path("error").asText("");
        if (!error.isBlank()) return error;
        String msg = data.path("message").asText("");
        return msg.isBlank() ? "Model request failed: " + status : msg;
    }

    private boolean shouldRetryStatus(int status) {
        return status == 429 || (status >= 500 && status < 600);
    }

    private long retryDelayMs(AnthropicTransport.Response response, int attempt) {
        Long retryAfter = parseRetryAfterMs(response);
        if (retryAfter != null) return retryAfter;
        long base = Math.min(BASE_RETRY_DELAY_MS * (1L << Math.max(0, Math.min(attempt - 1, 10))), MAX_RETRY_DELAY_MS);
        long jitter = Math.floorMod(Objects.hash(runtimeConfig.model(), attempt), Math.max(1L, base / 4L + 1L));
        return Math.min(MAX_RETRY_DELAY_MS, base + jitter);
    }

    private Long parseRetryAfterMs(AnthropicTransport.Response response) {
        if (response == null) return null;
        List<String> values = response.headers().get("retry-after");
        if (values == null) values = response.headers().get("Retry-After");
        if (values == null || values.isEmpty()) return null;
        String value = values.getFirst();
        try {
            double seconds = Double.parseDouble(value);
            if (seconds >= 0.0d) return Math.round(seconds * 1000.0d);
        } catch (NumberFormatException ignored) {}
        try {
            long epochMillis = java.time.ZonedDateTime.parse(value,
                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            return Math.max(0L, epochMillis - System.currentTimeMillis());
        } catch (RuntimeException ignored) { return null; }
    }

    private record ParsedText(String content, AssistantKind kind) {}
}
