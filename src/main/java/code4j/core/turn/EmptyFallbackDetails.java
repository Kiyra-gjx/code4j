package code4j.core.turn;

import java.util.Objects;
import java.util.Optional;

/**
 * Details for {@link AgentTurnStopReason#EMPTY_RESPONSE_FALLBACK}.
 * <p>
 * When the model returns no usable content, the agent injects a fallback message.
 * This record captures diagnostics about why the empty response happened,
 * which helps with debugging (e.g. "the model returned empty because every tool
 * had already errored out").
 */
public record EmptyFallbackDetails(Optional<String> reason, Optional<String> diagnostics,
                                   boolean sawToolResultThisTurn, int toolErrorCount)
        implements AgentTurnStopDetails {
    public EmptyFallbackDetails {
        reason = Objects.requireNonNull(reason, "reason");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        if (toolErrorCount < 0) {
            throw new IllegalArgumentException("toolErrorCount must be non-negative");
        }
    }

    public EmptyFallbackDetails(Optional<String> diagnostics) {
        this(diagnostics, diagnostics, false, 0);
    }
}
