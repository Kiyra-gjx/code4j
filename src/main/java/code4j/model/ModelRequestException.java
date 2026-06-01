package code4j.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Thrown when a model API request fails at the transport or HTTP level.
 * Not used for validation errors or cancellation.
 */
public final class ModelRequestException extends RuntimeException {
    private final boolean retryable;
    private final Optional<Integer> statusCode;
    private final Optional<String> diagnostics;

    public ModelRequestException(String message, boolean retryable, Optional<Integer> statusCode,
                                 Optional<String> diagnostics) {
        super(Objects.requireNonNull(message, "message"));
        this.retryable = retryable;
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public ModelRequestException(String message) {
        this(message, false, Optional.empty(), Optional.empty());
    }

    public boolean retryable() { return retryable; }
    public Optional<Integer> statusCode() { return statusCode; }
    public Optional<String> diagnostics() { return diagnostics; }
}
