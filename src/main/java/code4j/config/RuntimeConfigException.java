package code4j.config;

/**
 * Thrown when configuration is missing required values or cannot be parsed.
 * This is an unrecoverable error — the agent cannot start without valid config.
 */
public final class RuntimeConfigException extends RuntimeException {
    public RuntimeConfigException(String message) {
        super(message);
    }

    public RuntimeConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
