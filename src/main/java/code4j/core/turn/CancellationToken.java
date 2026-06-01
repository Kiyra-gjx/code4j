package code4j.core.turn;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A cooperative cancellation mechanism for agent turns.
 * <p>
 * <h3>Why cooperative (not preemptive)?</h3>
 * We can't safely kill a thread mid-API-call or mid-file-write. Instead,
 * the agent loop checks {@link #throwIfCancellationRequested} at key points
 * (before each API call, before each tool execution). If cancellation has been
 * requested, it throws {@link CancellationRequestedException}.
 * <p>
 * <h3>Thread safety</h3>
 * The cancellation request is stored in an {@link AtomicReference} so it can
 * be set from one thread (e.g. the UI thread handling Ctrl+C) and read from
 * another (the agent loop thread) without locks.
 * <p>
 * <h3>NONE token</h3>
 * {@link #none()} returns a shared singleton that silently ignores cancellation.
 * Used when there's no mechanism to request cancellation (e.g. in tests).
 */
public final class CancellationToken {
    private static final CancellationToken NONE = new CancellationToken(false);

    private final boolean cancellable;
    private final AtomicReference<CancellationRequest> request = new AtomicReference<>();

    private CancellationToken(boolean cancellable) {
        this.cancellable = cancellable;
    }

    /** A shared token that silently ignores all cancellation requests. */
    public static CancellationToken none() {
        return NONE;
    }

    /** Creates a new cancellable token. */
    public static CancellationToken create() {
        return new CancellationToken(true);
    }

    /** Creates a token that is already cancelled (useful for testing). */
    public static CancellationToken cancelled(CancellationSource source, String reason) {
        CancellationToken token = create();
        token.requestCancellation(source, reason);
        return token;
    }

    public boolean isCancellationRequested() {
        return request.get() != null;
    }

    /**
     * Requests cancellation. If the token is not cancellable (NONE),
     * the request is silently ignored. Uses {@code compareAndSet} so
     * only the first cancellation request is recorded.
     */
    public void requestCancellation(CancellationSource source, String reason) {
        if (!cancellable) {
            return;
        }
        CancellationRequest cancellationRequest = new CancellationRequest(source, reason);
        request.compareAndSet(null, cancellationRequest);
    }

    /** Returns the cancellation if one has been requested, with the given phase stamped in. */
    public Optional<TurnCancellation> cancellation(CancellationPhase phase) {
        CancellationRequest cancellationRequest = request.get();
        if (cancellationRequest == null) {
            return Optional.empty();
        }
        return Optional.of(cancellationRequest.toCancellation(phase));
    }

    /**
     * Throws {@link CancellationRequestedException} if cancellation has been requested.
     * Called at checkpoints throughout the agent loop.
     */
    public void throwIfCancellationRequested(CancellationPhase phase) {
        cancellation(phase).ifPresent(cancellation -> {
            throw new CancellationRequestedException(cancellation);
        });
    }

    /**
     * Internal record stored in the AtomicReference.
     * The {@code phase} is not stored here — it's filled in by {@link #cancellation(CancellationPhase)}
     * so the same underlying request can be reported with the correct phase at each checkpoint.
     */
    private record CancellationRequest(CancellationSource source, String reason) {
        private CancellationRequest {
            source = Objects.requireNonNull(source, "source");
            if (Objects.requireNonNull(reason, "reason").isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }

        private TurnCancellation toCancellation(CancellationPhase phase) {
            return new TurnCancellation(source, phase, reason);
        }
    }
}
