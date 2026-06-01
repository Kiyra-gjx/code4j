package code4j.core.turn;

/**
 * Why an agent turn stopped.
 * <p>
 * Each reason maps to specific {@link AgentTurnStopDetails}:
 * <ul>
 *   <li>{@link #FINAL} — model produced a final text response</li>
 *   <li>{@link #AWAIT_USER} — model called ask_user, waiting for input</li>
 *   <li>{@link #MAX_STEPS} — reached the max step limit for this turn</li>
 *   <li>{@link #MODEL_ERROR} — API call failed (requires {@link ModelErrorDetails})</li>
 *   <li>{@link #CANCELLED} — user or system cancelled (requires {@link CancellationDetails})</li>
 *   <li>{@link #EMPTY_RESPONSE_FALLBACK} — model returned empty, fallback text injected</li>
 * </ul>
 */
public enum AgentTurnStopReason {
    FINAL,
    AWAIT_USER,
    MAX_STEPS,
    MODEL_ERROR,
    CANCELLED,
    EMPTY_RESPONSE_FALLBACK
}
