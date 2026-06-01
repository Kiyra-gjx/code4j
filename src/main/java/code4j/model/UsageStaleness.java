package code4j.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Tracks whether the {@link ProviderUsage} attached to a message is still accurate.
 * <p>
 * Usage becomes stale after context compaction (summary replaces original messages),
 * because the token counts from the original model response no longer reflect the
 * current conversation state.
 */
public record UsageStaleness(boolean stale, Optional<String> reason) {
    public UsageStaleness {
        reason = Objects.requireNonNull(reason, "reason");
        if (stale && reason.filter(value -> !value.isBlank()).isEmpty()) {
            throw new IllegalArgumentException("stale usage requires a reason");
        }
        if (!stale && reason.isPresent()) {
            throw new IllegalArgumentException("fresh usage cannot carry a stale reason");
        }
    }

    public static UsageStaleness fresh() {
        return new UsageStaleness(false, Optional.empty());
    }

    public static UsageStaleness stale(String reason) {
        return new UsageStaleness(true, Optional.of(reason));
    }
}
