package code4j.config;

import code4j.mcp.McpServerConfig;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable runtime configuration for the agent.
 * <p>
 * Built by {@link RuntimeConfigLoader} from environment variables and settings files.
 * Uses multiple overloaded constructors (the "telescoping" pattern) so callers in
 * tests can create configs with minimal boilerplate.
 * <p>
 * Optional fields use {@link Optional} rather than null — this makes missing values
 * explicit and forces callers to handle the absence case.
 */
public record RuntimeConfig(ProviderKind provider, String model, String baseUrl, Optional<String> apiKey,
                            Optional<String> authToken, Optional<Integer> maxOutputTokens,
                            Optional<Integer> contextWindow, Optional<Integer> maxSteps,
                            Duration providerTimeout, String sourceSummary,
                            Map<String, McpServerConfig> mcpServers) {

    // --- convenience constructors for tests and simple callers ---

    public RuntimeConfig(ProviderKind provider, String model, String baseUrl, Optional<String> apiKey,
                         Optional<String> authToken, Optional<Integer> maxOutputTokens,
                         Optional<Integer> contextWindow, String sourceSummary) {
        this(provider, model, baseUrl, apiKey, authToken, maxOutputTokens, contextWindow, Optional.empty(),
                Duration.ofSeconds(300), sourceSummary, Map.of());
    }

    public RuntimeConfig(ProviderKind provider, String model, String baseUrl, Optional<String> apiKey,
                         Optional<String> authToken, Optional<Integer> maxOutputTokens,
                         Optional<Integer> contextWindow, Duration providerTimeout, String sourceSummary) {
        this(provider, model, baseUrl, apiKey, authToken, maxOutputTokens, contextWindow, Optional.empty(),
                providerTimeout, sourceSummary, Map.of());
    }

    public RuntimeConfig(ProviderKind provider, String model, String baseUrl, Optional<String> apiKey,
                         Optional<String> authToken, Optional<Integer> maxOutputTokens,
                         Optional<Integer> contextWindow, String sourceSummary,
                         Map<String, McpServerConfig> mcpServers) {
        this(provider, model, baseUrl, apiKey, authToken, maxOutputTokens, contextWindow, Optional.empty(),
                Duration.ofSeconds(300), sourceSummary, mcpServers);
    }

    // --- canonical constructor with full validation ---

    public RuntimeConfig(ProviderKind provider, String model, String baseUrl, Optional<String> apiKey,
                         Optional<String> authToken, Optional<Integer> maxOutputTokens,
                         Optional<Integer> contextWindow, Optional<Integer> maxSteps,
                         Duration providerTimeout, String sourceSummary,
                         Map<String, McpServerConfig> mcpServers) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.model = requireText(model, "model");
        this.baseUrl = requireText(baseUrl, "baseUrl");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.authToken = Objects.requireNonNull(authToken, "authToken");
        this.maxOutputTokens = Objects.requireNonNull(maxOutputTokens, "maxOutputTokens");
        this.contextWindow = Objects.requireNonNull(contextWindow, "contextWindow");
        this.maxSteps = requirePositiveOptional(maxSteps, "maxSteps");
        this.providerTimeout = requirePositive(providerTimeout, "providerTimeout");
        this.sourceSummary = requireText(sourceSummary, "sourceSummary");
        this.mcpServers = Map.copyOf(Objects.requireNonNull(mcpServers, "mcpServers"));
    }

    private static String requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        Duration actual = Objects.requireNonNull(value, name);
        if (actual.isZero() || actual.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return actual;
    }

    private static Optional<Integer> requirePositiveOptional(Optional<Integer> value, String name) {
        Optional<Integer> actual = Objects.requireNonNull(value, name);
        actual.ifPresent(number -> {
            if (number <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        });
        return actual;
    }
}
