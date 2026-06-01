package code4j.context.compact;

import code4j.context.boundary.ContextBoundaryGuard;
import code4j.context.stats.ContextStats;
import code4j.core.loop.ModelAdapter;
import code4j.core.message.ChatMessage;

import java.util.List;
import java.util.Objects;

public final class AutoCompactController {
    private final CompactService compactService;
    private final AutoCompactPolicy policy;
    private final boolean enabled;
    private int consecutiveFailures;
    private int cooldownRemaining;

    public AutoCompactController(CompactService compactService, AutoCompactPolicy policy) {
        this(compactService, policy, true);
    }

    private AutoCompactController(CompactService compactService, AutoCompactPolicy policy, boolean enabled) {
        this.compactService = Objects.requireNonNull(compactService, "compactService");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.enabled = enabled;
    }

    public static AutoCompactController disabled() {
        return new AutoCompactController(new CompactService(), AutoCompactPolicy.defaults(), false);
    }

    public boolean enabled() { return enabled; }

    public boolean willAttempt(List<ChatMessage> messages, ContextStats stats) {
        List<ChatMessage> m = List.copyOf(Objects.requireNonNull(messages, "messages"));
        Objects.requireNonNull(stats, "stats");
        return enabled && stats.effectiveInput() >= policy.minEffectiveInput()
                && stats.utilization() >= policy.utilizationThreshold()
                && ContextBoundaryGuard.isCompactSafeBoundary(m) && cooldownRemaining == 0;
    }

    public AutoCompactResult preflight(List<ChatMessage> messages, ContextStats stats, ModelAdapter adapter) {
        List<ChatMessage> m = List.copyOf(Objects.requireNonNull(messages, "messages"));
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(adapter, "adapter");
        if (!enabled) return AutoCompactResult.skipped(m, "auto compact disabled");
        if (stats.effectiveInput() < policy.minEffectiveInput()) {
            resetFailures();
            return AutoCompactResult.skipped(m, "below auto compact minimum");
        }
        if (stats.utilization() < policy.utilizationThreshold()) {
            resetFailures();
            return AutoCompactResult.skipped(m, "below auto compact threshold");
        }
        if (!ContextBoundaryGuard.isCompactSafeBoundary(m)) {
            return AutoCompactResult.skipped(m, "unsafe compact boundary: incomplete tool round");
        }
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
            return AutoCompactResult.skipped(m, "auto compact cooldown after failure");
        }
        ManualCompactResult result = compactService.compact(new CompactRequest(m, adapter, CompactTrigger.AUTO));
        if (result.status() == CompactStatus.COMPACTED) {
            consecutiveFailures = 0;
            cooldownRemaining = 0;
            return AutoCompactResult.compacted(result.messages(), result.boundary().orElseThrow());
        }
        if (result.status() == CompactStatus.FAILED) {
            recordFailure();
            return AutoCompactResult.failed(m, result.reason().orElse("auto compact failed"));
        }
        return AutoCompactResult.skipped(m, result.reason().orElse("auto compact skipped"));
    }

    private void recordFailure() {
        consecutiveFailures = Math.min(policy.maxFailures(), consecutiveFailures + 1);
        cooldownRemaining = policy.failureCooldownPreflights() * consecutiveFailures;
    }

    private void resetFailures() { consecutiveFailures = 0; cooldownRemaining = 0; }
}
