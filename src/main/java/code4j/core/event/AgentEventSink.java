package code4j.core.event;

import java.util.Objects;

/**
 * Receiver of agent events. The TUI layer implements this to render status updates.
 * <p>
 * {@link #noOp()} returns a sink that silently accepts all events — useful for
 * batch mode or tests that don't need UI.
 */
@FunctionalInterface
public interface AgentEventSink {
    void onEvent(AgentEvent event);

    static AgentEventSink noOp() {
        return event -> Objects.requireNonNull(event, "event");
    }
}
