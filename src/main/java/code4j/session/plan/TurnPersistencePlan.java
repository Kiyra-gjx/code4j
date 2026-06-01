package code4j.session.plan;

import java.util.List;
import java.util.Objects;

/**
 * A list of persistence actions to execute after a turn.
 * Returned as part of {@link code4j.core.turn.AgentTurnResult} so the session
 * layer knows what to write without the agent loop knowing about persistence details.
 */
public record TurnPersistencePlan(List<PersistenceAction> actions) {
    public TurnPersistencePlan {
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    }

    public static TurnPersistencePlan empty() {
        return new TurnPersistencePlan(List.of());
    }
}
