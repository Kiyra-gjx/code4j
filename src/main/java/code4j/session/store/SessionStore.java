package code4j.session.store;

import code4j.context.compact.CompactMetadata;
import code4j.context.compact.CompactTrigger;
import code4j.core.message.*;
import code4j.model.ProviderThinkingBlock;
import code4j.model.ProviderUsage;
import code4j.model.UsageStaleness;
import code4j.session.model.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * JSONL-based session persistence. Each session is a single .jsonl file,
 * one JSON object per line. This makes sessions append-only, human-readable,
 * and easy to backup/restore.
 */
public final class SessionStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path root;

    public SessionStore(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public void append(SessionEvent event) {
        Objects.requireNonNull(event, "event");
        Path file = sessionFile(event.sessionId(), event.cwd());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, serialize(event) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<SessionEvent> readAll(String sessionId, String cwd) {
        return readAllFromPath(sessionFile(sessionId, cwd));
    }

    public Optional<String> latestEventUuid(String sessionId, String cwd) {
        List<SessionEvent> events = readAll(sessionId, cwd);
        if (events.isEmpty()) return Optional.empty();
        return Optional.of(events.getLast().uuid());
    }

    public List<ChatMessage> loadMessagesSinceLatestCompactBoundary(String sessionId, String cwd) {
        List<SessionEvent> events = readAll(sessionId, cwd);
        int start = 0;
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).type() == SessionEventType.COMPACT_BOUNDARY) { start = i + 1; break; }
        }
        return events.subList(start, events.size()).stream()
                .flatMap(e -> e.message().stream()).toList();
    }

    public List<SessionMetadata> listSessionsByCwd(String cwd) {
        Path dir = root.resolve(cwdDirectoryKey(cwd));
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .map(p -> readMetadataFromPath(cwd, p))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(SessionMetadata::updatedAt).reversed())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<SessionMetadata> readMetadata(String sessionId, String cwd) {
        Path file = sessionFile(sessionId, cwd);
        if (!Files.exists(file)) return Optional.empty();
        return readMetadataFromPath(cwd, file);
    }

    public List<String> findCwdsForSessionId(String sessionId) {
        String fileName = sanitize(sessionId) + ".jsonl";
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve(fileName)))
                    .map(this::cwdFromFirstEvent)
                    .flatMap(Optional::stream).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path sessionFile(String sessionId, String cwd) {
        return root.resolve(cwdDirectoryKey(cwd)).resolve(sanitize(sessionId) + ".jsonl");
    }

    private Optional<SessionMetadata> readMetadataFromPath(String cwd, Path file) {
        try {
            String fn = file.getFileName().toString();
            String sessionId = fn.substring(0, fn.length() - ".jsonl".length());
            List<SessionEvent> events = readAllFromPath(file);
            if (events.isEmpty() || events.stream().anyMatch(e -> !e.cwd().equals(cwd))) return Optional.empty();
            FileTime modified = Files.getLastModifiedTime(file);
            Optional<String> title = latestTitle(events).or(() -> firstUserTitle(events));
            return Optional.of(new SessionMetadata(sessionId, cwd, title, events.size(),
                    modified.toInstant(), file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<SessionEvent> readAllFromPath(Path file) {
        if (!Files.exists(file)) return List.of();
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(this::deserialize).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<String> cwdFromFirstEvent(Path dir) {
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> first = paths.filter(p -> p.getFileName().toString().endsWith(".jsonl")).findFirst();
            if (first.isEmpty()) return Optional.empty();
            List<String> lines = Files.readAllLines(first.orElseThrow(), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (!line.isBlank()) return Optional.of(deserialize(line).cwd());
            }
        } catch (IOException ignored) {}
        return Optional.empty();
    }

    private static Optional<String> latestTitle(List<SessionEvent> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            Optional<MetaSessionEventDraft> meta = events.get(i).meta();
            if (meta.isPresent() && meta.orElseThrow() instanceof RenameDraft r) return Optional.of(r.title());
        }
        return Optional.empty();
    }

    private static Optional<String> firstUserTitle(List<SessionEvent> events) {
        return events.stream().flatMap(e -> e.message().stream())
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).content().trim())
                .filter(c -> !c.isBlank())
                .findFirst().map(c -> c.length() > 60 ? c.substring(0, 60) : c);
    }

    private static String sanitize(String value) {
        String s = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isBlank() ? "_" : s;
    }

    private static String cwdDirectoryKey(String cwd) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Objects.requireNonNull(cwd, "cwd").getBytes(StandardCharsets.UTF_8));
    }

    // -- serialization --

    private String serialize(SessionEvent event) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", serializedEventType(event));
        root.put("uuid", event.uuid());
        root.put("timestamp", event.timestamp().toString());
        root.put("sessionId", event.sessionId());
        root.put("cwd", event.cwd());
        event.parentUuid().ifPresent(v -> root.put("parentUuid", v));
        event.logicalParentUuid().ifPresent(v -> root.put("logicalParentUuid", v));
        event.message().ifPresent(m -> root.set("message", serializeMessage(m)));
        event.meta().ifPresent(m -> root.set("meta", serializeMeta(m)));
        event.compactMetadata().ifPresent(cm -> root.set("compactMetadata", serializeCompactMetadata(cm)));
        try { return MAPPER.writeValueAsString(root); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private SessionEvent deserialize(String line) {
        try {
            JsonNode root = MAPPER.readTree(line);
            SessionEventType type = deserializeEventType(root.get("type").asText());
            Optional<String> pu = optText(root, "parentUuid");
            Optional<String> lpu = optText(root, "logicalParentUuid");
            Optional<ChatMessage> msg = root.has("message") ? Optional.of(deserializeMessage(root.get("message"))) : Optional.empty();
            Optional<MetaSessionEventDraft> meta = root.has("meta") ? Optional.of(deserializeMeta(type, root.get("meta"))) : Optional.empty();
            Optional<CompactMetadata> cm = root.has("compactMetadata") ? Optional.of(deserializeCompactMetadata(root.get("compactMetadata"))) : Optional.empty();
            return new SessionEvent(type, root.get("uuid").asText(), Instant.parse(root.get("timestamp").asText()),
                    root.get("sessionId").asText(), root.get("cwd").asText(), pu, lpu, msg, meta, cm);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid session JSONL event", e);
        }
    }

    private ObjectNode serializeMessage(ChatMessage message) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (message) {
            case SystemMessage m -> { node.put("role", "system"); node.put("content", m.content()); }
            case UserMessage m -> { node.put("role", "user"); node.put("content", m.content()); }
            case AssistantMessage m -> {
                node.put("role", "assistant"); node.put("content", m.content());
                writeUsage(node, m.providerUsage(), m.usageStaleness());
            }
            case AssistantProgressMessage m -> {
                node.put("role", "assistant_progress"); node.put("content", m.content());
                writeUsage(node, m.providerUsage(), m.usageStaleness());
            }
            case AssistantToolCallMessage m -> {
                node.put("role", "assistant_tool_call");
                node.put("toolUseId", m.toolUseId()); node.put("toolName", m.toolName());
                node.set("input", m.input());
                writeUsage(node, m.providerUsage(), m.usageStaleness());
            }
            case AssistantThinkingMessage m -> {
                node.put("role", "assistant_thinking");
                var blocks = node.putArray("blocks");
                for (ProviderThinkingBlock b : m.blocks()) {
                    var bn = blocks.addObject(); bn.put("type", b.type()); bn.set("raw", b.raw());
                }
            }
            case ContextSummaryMessage m -> {
                node.put("role", "context_summary"); node.put("content", m.content());
                node.put("compressedCount", m.compressedCount());
                node.put("timestamp", m.timestamp().toString());
            }
            case ToolResultMessage m -> {
                node.put("role", "tool_result");
                node.put("toolUseId", m.toolUseId()); node.put("toolName", m.toolName());
                node.put("content", m.content()); node.put("error", m.error());
            }
            default -> throw new IllegalArgumentException("Unsupported message: " + message.getClass().getSimpleName());
        }
        return node;
    }

    private String serializedEventType(SessionEvent event) {
        if (event.type() == SessionEventType.MESSAGE && event.message().isPresent()) {
            return switch (event.message().orElseThrow()) {
                case SystemMessage ignored -> "system";
                case UserMessage ignored -> "user";
                case AssistantMessage ignored -> "assistant";
                case AssistantThinkingMessage ignored -> "thinking";
                case AssistantProgressMessage ignored -> "progress";
                case AssistantToolCallMessage ignored -> "tool_call";
                case ToolResultMessage ignored -> "tool_result";
                case ContextSummaryMessage ignored -> "summary";
                default -> "message";
            };
        }
        return switch (event.type()) {
            case MESSAGE -> "message";
            case COMPACT_BOUNDARY -> "compact_boundary";
            case RENAME -> "rename";
            case FORK -> "fork";
        };
    }

    private SessionEventType deserializeEventType(String value) {
        return switch (value) {
            case "system","user","assistant","thinking","progress","tool_call","tool_result","summary","message","MESSAGE" -> SessionEventType.MESSAGE;
            case "compact_boundary","COMPACT_BOUNDARY" -> SessionEventType.COMPACT_BOUNDARY;
            case "rename","RENAME" -> SessionEventType.RENAME;
            case "fork","FORK" -> SessionEventType.FORK;
            default -> SessionEventType.valueOf(value);
        };
    }

    private ChatMessage deserializeMessage(JsonNode node) {
        return switch (node.get("role").asText()) {
            case "system" -> new SystemMessage(node.get("content").asText());
            case "user" -> new UserMessage(node.get("content").asText());
            case "assistant" -> new AssistantMessage(node.get("content").asText(), readUsage(node), readStaleness(node));
            case "assistant_progress" -> new AssistantProgressMessage(node.get("content").asText(), readUsage(node), readStaleness(node));
            case "assistant_tool_call" -> new AssistantToolCallMessage(node.get("toolUseId").asText(), node.get("toolName").asText(), node.get("input"), readUsage(node), readStaleness(node));
            case "assistant_thinking" -> {
                List<ProviderThinkingBlock> blocks = new ArrayList<>();
                for (JsonNode b : node.get("blocks")) blocks.add(new ProviderThinkingBlock(b.get("type").asText(), b.get("raw")));
                yield new AssistantThinkingMessage(blocks);
            }
            case "context_summary" -> new ContextSummaryMessage(node.get("content").asText(), node.get("compressedCount").asInt(), Instant.parse(node.get("timestamp").asText()));
            case "tool_result" -> new ToolResultMessage(node.get("toolUseId").asText(), node.get("toolName").asText(), node.get("content").asText(), node.get("error").asBoolean());
            default -> throw new IllegalArgumentException("Unknown role: " + node.get("role").asText());
        };
    }

    private void writeUsage(ObjectNode node, Optional<ProviderUsage> usage, UsageStaleness staleness) {
        usage.ifPresent(u -> {
            var un = node.putObject("providerUsage");
            un.put("inputTokens", u.inputTokens()); un.put("outputTokens", u.outputTokens()); un.put("totalTokens", u.totalTokens());
        });
        var sn = node.putObject("usageStaleness");
        sn.put("stale", staleness.stale());
        staleness.reason().ifPresent(r -> sn.put("reason", r));
    }

    private Optional<ProviderUsage> readUsage(JsonNode node) {
        if (!node.has("providerUsage")) return Optional.empty();
        JsonNode u = node.get("providerUsage");
        return Optional.of(new ProviderUsage(u.get("inputTokens").asInt(), u.get("outputTokens").asInt(), u.get("totalTokens").asInt()));
    }

    private UsageStaleness readStaleness(JsonNode node) {
        if (!node.has("usageStaleness")) return UsageStaleness.fresh();
        JsonNode s = node.get("usageStaleness");
        if (!s.get("stale").asBoolean()) return UsageStaleness.fresh();
        return UsageStaleness.stale(s.get("reason").asText());
    }

    private ObjectNode serializeMeta(MetaSessionEventDraft meta) {
        ObjectNode node = MAPPER.createObjectNode();
        if (meta instanceof RenameDraft r) { node.put("title", r.title()); }
        else if (meta instanceof ForkDraft f) {
            var m = node.putObject("metadata");
            m.put("sourceSessionId", f.metadata().sourceSessionId());
            f.metadata().sourceEventId().ifPresent(v -> m.put("sourceEventId", v));
            m.put("newSessionId", f.metadata().newSessionId());
            m.put("cwd", f.metadata().cwd());
            m.put("timestamp", f.metadata().timestamp().toString());
        }
        return node;
    }

    private MetaSessionEventDraft deserializeMeta(SessionEventType type, JsonNode node) {
        return switch (type) {
            case RENAME -> new RenameDraft(node.get("title").asText());
            case FORK -> {
                JsonNode m = node.get("metadata");
                yield new ForkDraft(new ForkMetadata(m.get("sourceSessionId").asText(),
                        optText(m, "sourceEventId"), m.get("newSessionId").asText(),
                        m.get("cwd").asText(), Instant.parse(m.get("timestamp").asText())));
            }
            default -> throw new IllegalArgumentException(type + " does not carry meta");
        };
    }

    private ObjectNode serializeCompactMetadata(CompactMetadata cm) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("trigger", cm.trigger().name()); n.put("tokensBefore", cm.tokensBefore());
        n.put("tokensAfter", cm.tokensAfter()); n.put("messagesCompressed", cm.messagesCompressed());
        n.put("timestamp", cm.timestamp().toString());
        return n;
    }

    private CompactMetadata deserializeCompactMetadata(JsonNode node) {
        return new CompactMetadata(CompactTrigger.valueOf(node.get("trigger").asText()),
                node.get("tokensBefore").asLong(), node.get("tokensAfter").asLong(),
                node.get("messagesCompressed").asInt(), Instant.parse(node.get("timestamp").asText()));
    }

    private static Optional<String> optText(JsonNode node, String field) {
        return node.has(field) ? Optional.of(node.get(field).asText()) : Optional.empty();
    }
}
