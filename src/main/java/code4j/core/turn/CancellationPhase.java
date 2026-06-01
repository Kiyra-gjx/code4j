package code4j.core.turn;

/**
 * The phase of the agent loop where cancellation was detected.
 * Used to provide precise error messages (e.g. "cancelled during tool execution").
 */
public enum CancellationPhase {
    BEFORE_TURN,
    MODEL_REQUEST,
    PERMISSION_PROMPT,
    TOOL_EXECUTION,
    AFTER_TURN
}
