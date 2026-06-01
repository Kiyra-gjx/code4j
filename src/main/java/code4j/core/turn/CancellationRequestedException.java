package code4j.core.turn;

import java.util.Objects;

/**
 * Thrown when a cancellation is detected during agent loop execution.
 * Carries the full {@link TurnCancellation} record so the catcher knows
 * exactly what happened without parsing the exception message.
 */
public final class CancellationRequestedException extends RuntimeException {
    private final TurnCancellation cancellation;

    public CancellationRequestedException(TurnCancellation cancellation) {
        super(Objects.requireNonNull(cancellation, "cancellation").reason());
        this.cancellation = cancellation;
    }

    public TurnCancellation cancellation() {
        return cancellation;
    }
}
