package code4j.core.turn;

import java.util.Objects;

/**
 * Details for {@link AgentTurnStopReason#MODEL_ERROR}.
 * The error source must be {@link TurnErrorSource#MODEL}.
 */
public record ModelErrorDetails(TurnError error) implements AgentTurnStopDetails {
    public ModelErrorDetails {
        error = Objects.requireNonNull(error, "error");
        if (error.source() != TurnErrorSource.MODEL) {
            throw new IllegalArgumentException("model error details require MODEL source");
        }
    }
}
