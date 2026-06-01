package code4j.core.turn;

/**
 * Additional information about why a turn stopped.
 * Different stop reasons carry different detail types — this sealed interface
 * ensures the compiler knows all possible detail types.
 */
public sealed interface AgentTurnStopDetails permits ModelErrorDetails, CancellationDetails, EmptyFallbackDetails {
}
