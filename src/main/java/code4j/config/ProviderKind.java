package code4j.config;

/**
 * Supported model provider types.
 * <p>
 * {@link #MOCK} is for testing without real API calls.
 * {@link #ANTHROPIC} covers both native Anthropic and Anthropic-compatible proxies.
 */
public enum ProviderKind {
    MOCK,
    ANTHROPIC;

    /**
     * Parses a provider string. Defaults to {@link #ANTHROPIC} when the value
     * is null or blank, so users don't need to set this for normal use.
     *
     * @throws RuntimeConfigException for unrecognized provider values
     */
    public static ProviderKind parse(String value) {
        if (value == null || value.isBlank()) {
            return ANTHROPIC;
        }
        return switch (value.trim().toLowerCase()) {
            case "mock" -> MOCK;
            case "anthropic", "anthropic-compatible" -> ANTHROPIC;
            default -> throw new RuntimeConfigException("Unsupported provider: " + value);
        };
    }
}
