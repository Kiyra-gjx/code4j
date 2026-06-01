package code4j.core.turn;

import java.util.Objects;
import java.util.Optional;

/**
 * An error that occurred during a turn, with enough context for the caller
 * to decide whether to retry, report, or abort.
 */
public record TurnError(String message, TurnErrorSource source, boolean retryable,
                        Optional<String> diagnostics, Optional<String> causeClass) {
    public TurnError {
        if (Objects.requireNonNull(message, "message").isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        source = Objects.requireNonNull(source, "source");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        causeClass = Objects.requireNonNull(causeClass, "causeClass");
    }
}
