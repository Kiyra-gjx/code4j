package code4j.tools.builtin;

import java.time.Duration;
import java.util.Objects;

public final class CommandTimeoutPolicy {
    private final Duration defaultTimeout, maxTimeout;

    public CommandTimeoutPolicy() { this(Duration.ofSeconds(5), Duration.ofSeconds(60)); }
    public CommandTimeoutPolicy(Duration d, Duration m) { if (d.isZero()||d.isNegative()||m.isZero()||m.isNegative()||d.compareTo(m)>0) throw new IllegalArgumentException("invalid timeout"); this.defaultTimeout = d; this.maxTimeout = m; }

    public Duration timeoutFor(Integer s) { return s == null ? defaultTimeout : Duration.ofSeconds(s); }
    public int maxTimeoutSeconds() { return Math.toIntExact(maxTimeout.toSeconds()); }
}
