package code4j.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ModelLimits {
    private static final MaxOutputTokens UNKNOWN_MAX_OUTPUT = new MaxOutputTokens(32_000, 64_000);
    private static final ContextWindow UNKNOWN_CONTEXT = new ContextWindow(128_000, 8_000);

    private static final List<MaxOutputRule> MAX_OUTPUT_RULES = List.of(
            rule(List.of("claude-opus-4-6", "claude opus 4.6", "opus-4-6"), 128_000, 128_000),
            rule(List.of("claude-sonnet-4-6", "claude sonnet 4.6", "sonnet-4-6"), 64_000, 64_000),
            rule(List.of("claude-haiku-4-5", "claude haiku 4.5", "haiku-4-5"), 64_000, 64_000),
            rule(List.of("deepseek-v4-pro", "deepseek v4 pro"), 64_000, 64_000),
            rule(List.of("mimo-v2.5-pro", "mimo v2.5 pro"), 16_000, 64_000),
            rule(List.of("claude-3-7-sonnet", "claude 3.7 sonnet"), 8_192, 8_192),
            rule(List.of("claude-3-5-sonnet", "claude 3.5 sonnet", "claude-3-sonnet"), 8_192, 8_192));

    private static final List<ContextRule> CONTEXT_RULES = List.of(
            contextRule(List.of("claude-opus-4-6", "opus-4-6"), 200_000, 16_000),
            contextRule(List.of("claude-sonnet-4-6", "sonnet-4-6"), 200_000, 16_000),
            contextRule(List.of("deepseek-v4-pro", "deepseek v4 pro"), 1_000_000, 16_000),
            contextRule(List.of("mimo-v2.5-pro", "mimo v2.5 pro"), 1_048_576, 16_000),
            contextRule(List.of("claude-3-7-sonnet"), 200_000, 8_192),
            contextRule(List.of("claude-3-5-sonnet", "claude-3-sonnet"), 200_000, 8_192));

    private ModelLimits() {}

    public static int resolveMaxOutputTokens(String model, Optional<Integer> configured) {
        MaxOutputTokens limits = maxOutputTokens(model);
        if (configured.isPresent() && configured.orElseThrow() > 0) {
            return Math.min(configured.orElseThrow(), limits.upperLimit());
        }
        return limits.defaultValue();
    }

    public static ContextWindow contextWindow(String model) {
        String n = normalize(model);
        for (ContextRule rule : CONTEXT_RULES) {
            if (rule.matches(n)) return rule.window();
        }
        return UNKNOWN_CONTEXT;
    }

    public static boolean isKnownContextModel(String model) {
        String n = normalize(model);
        return CONTEXT_RULES.stream().anyMatch(r -> r.matches(n));
    }

    private static MaxOutputTokens maxOutputTokens(String model) {
        String n = normalize(model);
        for (MaxOutputRule r : MAX_OUTPUT_RULES) {
            if (r.matches(n)) return r.limits();
        }
        return UNKNOWN_MAX_OUTPUT;
    }

    private static String normalize(String model) {
        return Objects.requireNonNull(model, "model").trim().toLowerCase(Locale.ROOT);
    }

    private static MaxOutputRule rule(List<String> patterns, int defaultValue, int upperLimit) {
        return new MaxOutputRule(patterns, new MaxOutputTokens(defaultValue, upperLimit));
    }

    private static ContextRule contextRule(List<String> patterns, long contextWindow, long outputReserve) {
        return new ContextRule(patterns, new ContextWindow(contextWindow, outputReserve));
    }

    public record ContextWindow(long contextWindow, long outputReserve) {
        public ContextWindow {
            if (contextWindow <= 0 || outputReserve < 0) {
                throw new IllegalArgumentException("invalid context window");
            }
        }
    }

    private record MaxOutputTokens(int defaultValue, int upperLimit) {
        MaxOutputTokens {
            if (defaultValue <= 0 || upperLimit <= 0) {
                throw new IllegalArgumentException("max output token limits must be positive");
            }
        }
    }

    private record MaxOutputRule(List<String> patterns, MaxOutputTokens limits) {
        MaxOutputRule { patterns = List.copyOf(patterns); limits = Objects.requireNonNull(limits, "limits"); }
        boolean matches(String normalized) { return patterns.stream().anyMatch(normalized::contains); }
    }

    private record ContextRule(List<String> patterns, ContextWindow window) {
        ContextRule { patterns = List.copyOf(patterns); window = Objects.requireNonNull(window, "window"); }
        boolean matches(String normalized) { return patterns.stream().anyMatch(normalized::contains); }
    }
}
