package code4j.core.turn;

import java.util.Objects;

/**
 * A complete cancellation record: who cancelled it, during which phase, and why.
 */
public record TurnCancellation(CancellationSource source, CancellationPhase phase, String reason) {
    public TurnCancellation {
        source = Objects.requireNonNull(source, "source");
        phase = Objects.requireNonNull(phase, "phase");
        if (Objects.requireNonNull(reason, "reason").isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
