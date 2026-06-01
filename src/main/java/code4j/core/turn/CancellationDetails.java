package code4j.core.turn;

import java.util.Objects;

/**
 * Details for {@link AgentTurnStopReason#CANCELLED}.
 */
public record CancellationDetails(TurnCancellation cancellation) implements AgentTurnStopDetails {
    public CancellationDetails {
        cancellation = Objects.requireNonNull(cancellation, "cancellation");
    }
}
