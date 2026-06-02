package code4j.permissions.store;

import code4j.permissions.model.PermissionKind;
import code4j.permissions.model.PermissionResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class JsonPermissionStore implements PermissionStore {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path file;
    private final Map<PermissionResourceKey, PermissionStoreEntry> entries = new LinkedHashMap<>();
    private boolean loaded;

    public JsonPermissionStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    @Override
    public synchronized Optional<PermissionStoreEntry> find(PermissionResource resource) {
        loadIfNeeded();
        return Optional.ofNullable(entries.get(PermissionResourceKey.from(resource)));
    }

    @Override
    public synchronized void save(PermissionStoreEntry entry) {
        loadIfNeeded();
        entries.put(Objects.requireNonNull(entry, "entry").resourceKey(), entry);
        write();
    }

    @Override
    public synchronized List<PermissionStoreEntry> entries() {
        loadIfNeeded();
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    private void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(file)) return;
        try {
            JsonNode root = MAPPER.readTree(file.toFile());
            JsonNode arr = root.get("entries");
            if (arr == null || !arr.isArray()) return;
            for (JsonNode en : arr) {
                JsonNode kn = en.path("resourceKey");
                PermissionResourceKey key = new PermissionResourceKey(
                        requiredText(kn, "type"), requiredText(kn, "fingerprint"));
                PermissionStoreEntry e = new PermissionStoreEntry(
                        PermissionStoreDecision.valueOf(requiredText(en, "decision")),
                        PermissionKind.valueOf(requiredText(en, "kind")), key,
                        Instant.parse(requiredText(en, "createdAt")));
                entries.put(key, e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read permission store " + file, e);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new IllegalArgumentException("Invalid field: " + field);
        }
        return v.asText();
    }

    private void write() {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            ObjectNode root = MAPPER.createObjectNode();
            root.put("version", 1);
            ArrayNode arr = root.putArray("entries");
            for (PermissionStoreEntry e : entries.values()) {
                ObjectNode en = arr.addObject();
                en.put("decision", e.decision().name());
                en.put("kind", e.kind().name());
                ObjectNode kn = en.putObject("resourceKey");
                kn.put("type", e.resourceKey().type());
                kn.put("fingerprint", e.resourceKey().fingerprint());
                en.put("createdAt", e.createdAt().toString());
            }
            MAPPER.writeValue(file.toFile(), root);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write permission store " + file, e);
        }
    }
}
