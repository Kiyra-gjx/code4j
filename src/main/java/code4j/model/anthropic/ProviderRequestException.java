package code4j.model.anthropic;

import code4j.model.ModelRequestException;

import java.util.Optional;

public final class ProviderRequestException extends ModelRequestException {
    public ProviderRequestException(String message) {
        this(message, Optional.empty(), false, null);
    }

    public ProviderRequestException(String message, Optional<Integer> statusCode, boolean retryable) {
        this(message, statusCode, retryable, null);
    }

    public ProviderRequestException(String message, Optional<Integer> statusCode, boolean retryable, Throwable cause) {
        super(message, statusCode, retryable, cause);
    }
}
