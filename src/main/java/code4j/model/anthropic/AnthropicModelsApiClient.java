package code4j.model.anthropic;

import code4j.config.RuntimeConfig;
import code4j.model.ModelMetadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AnthropicModelsApiClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RuntimeConfig runtimeConfig;
    private final AnthropicTransport transport;

    public AnthropicModelsApiClient(RuntimeConfig runtimeConfig, AnthropicTransport transport) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public AnthropicTransport transport() { return transport; }

    public Optional<ModelMetadata> fetch(String modelId) {
        String id = Objects.requireNonNull(modelId, "modelId");
        if (id.isBlank()) return Optional.empty();
        AnthropicTransport.Response response;
        try {
            response = transport.get(modelsUrl(id), headers());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (!response.ok()) return Optional.empty();
        try {
            JsonNode root = MAPPER.readTree(response.body());
            Optional<Long> maxInput = root.has("max_input_tokens")
                    ? Optional.of(root.get("max_input_tokens").asLong()) : Optional.empty();
            Optional<Integer> maxOutput = root.has("max_tokens")
                    ? Optional.of(root.get("max_tokens").asInt()) : Optional.empty();
            if (maxInput.isEmpty() && maxOutput.isEmpty()) return Optional.empty();
            String mid = root.path("id").asText(id);
            return Optional.of(new ModelMetadata(mid, maxInput, maxOutput));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private String modelsUrl(String modelId) {
        return runtimeConfig.baseUrl().replaceAll("/+$", "") + "/v1/models/" + modelId;
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("anthropic-version", "2023-06-01");
        runtimeConfig.authToken().ifPresent(t -> h.put("Authorization", "Bearer " + t));
        if (runtimeConfig.authToken().isEmpty()) {
            runtimeConfig.apiKey().ifPresent(k -> h.put("x-api-key", k));
        }
        return h;
    }
}
