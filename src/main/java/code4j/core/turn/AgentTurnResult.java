package code4j.core.turn;

import code4j.core.message.ChatMessage;
import code4j.session.plan.TurnPersistencePlan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of executing one agent turn.
 * <p>
 * Contains the messages produced during the turn, a persistence plan for the
 * session layer, and the reason + details for why the turn stopped.
 * <p>
 * Use the static factory methods ({@link #finalResult}, {@link #awaitUser}, etc.)
 * rather than calling the constructor directly — they enforce the correct
 * stop-reason-to-details mapping.
 */
public record AgentTurnResult(List<ChatMessage> messages, TurnPersistencePlan persistencePlan,
                              AgentTurnStopReason stopReason, Optional<AgentTurnStopDetails> stopDetails) {
    public AgentTurnResult {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        persistencePlan = Objects.requireNonNull(persistencePlan, "persistencePlan");
        stopReason = Objects.requireNonNull(stopReason, "stopReason");
        stopDetails = Objects.requireNonNull(stopDetails, "stopDetails");
        validate(stopReason, stopDetails);
    }

    // --- factory methods ---

    public static AgentTurnResult create(List<ChatMessage> messages, TurnPersistencePlan persistencePlan,
                                         AgentTurnStopReason stopReason,
                                         Optional<AgentTurnStopDetails> stopDetails) {
        return new AgentTurnResult(messages, persistencePlan, stopReason, stopDetails);
    }

    /** Model produced a final text response — turn complete. */
    public static AgentTurnResult finalResult(List<ChatMessage> messages, TurnPersistencePlan persistencePlan) {
        return create(messages, persistencePlan, AgentTurnStopReason.FINAL, Optional.empty());
    }

    /** Model called ask_user — the agent is waiting for the user to respond. */
    public static AgentTurnResult awaitUser(List<ChatMessage> messages, TurnPersistencePlan persistencePlan) {
        return create(messages, persistencePlan, AgentTurnStopReason.AWAIT_USER, Optional.empty());
    }

    /** Turn reached the max step limit. */
    public static AgentTurnResult maxSteps(List<ChatMessage> messages, TurnPersistencePlan persistencePlan) {
        return create(messages, persistencePlan, AgentTurnStopReason.MAX_STEPS, Optional.empty());
    }

    /** Model API call failed. */
    public static AgentTurnResult modelError(List<ChatMessage> messages, TurnPersistencePlan persistencePlan,
                                             ModelErrorDetails details) {
        return create(messages, persistencePlan, AgentTurnStopReason.MODEL_ERROR, Optional.of(details));
    }

    /** Turn was cancelled by user or system. */
    public static AgentTurnResult cancelled(List<ChatMessage> messages, TurnPersistencePlan persistencePlan,
                                            CancellationDetails details) {
        return create(messages, persistencePlan, AgentTurnStopReason.CANCELLED, Optional.of(details));
    }

    /** Model returned empty — a fallback message was injected. */
    public static AgentTurnResult emptyFallback(List<ChatMessage> messages, TurnPersistencePlan persistencePlan,
                                                Optional<EmptyFallbackDetails> details) {
        return create(messages, persistencePlan, AgentTurnStopReason.EMPTY_RESPONSE_FALLBACK,
                details.map(AgentTurnStopDetails.class::cast));
    }

    // --- validation ---

    private static void validate(AgentTurnStopReason reason, Optional<AgentTurnStopDetails> details) {
        switch (reason) {
            case FINAL, AWAIT_USER, MAX_STEPS -> {
                if (details.isPresent()) {
                    throw new IllegalArgumentException(reason + " cannot carry stop details");
                }
            }
            case MODEL_ERROR -> requireDetails(details, ModelErrorDetails.class, reason);
            case CANCELLED -> requireDetails(details, CancellationDetails.class, reason);
            case EMPTY_RESPONSE_FALLBACK -> {
                if (details.isPresent() && !(details.get() instanceof EmptyFallbackDetails)) {
                    throw new IllegalArgumentException(reason + " requires EmptyFallbackDetails");
                }
            }
        }
    }

    private static void requireDetails(Optional<AgentTurnStopDetails> details,
                                       Class<? extends AgentTurnStopDetails> type,
                                       AgentTurnStopReason reason) {
        if (details.isEmpty() || !type.isInstance(details.get())) {
            throw new IllegalArgumentException(reason + " requires " + type.getSimpleName());
        }
    }
}
