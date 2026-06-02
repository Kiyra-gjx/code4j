package code4j.session.factory;

import code4j.context.compact.CompactMetadata;
import code4j.core.message.ChatMessage;
import code4j.session.model.MetaSessionEventDraft;
import code4j.session.model.SessionEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Creates SessionEvents with a UUID chain (each event's parentUuid points to the previous event).
 */
public final class SessionEventFactory {
    private final String sessionId;
    private final String cwd;
    private final Clock clock;
    private final Supplier<String> uuidSupplier;
    private Optional<String> lastEventUuid;

    public SessionEventFactory(String sessionId, String cwd) {
        this(sessionId, cwd, Clock.systemUTC(), () -> java.util.UUID.randomUUID().toString(), Optional.empty());
    }

    public SessionEventFactory(String sessionId, String cwd, Clock clock, Supplier<String> uuidSupplier,
                               Optional<String> lastEventUuid) {
        this.sessionId = requireText(sessionId, "sessionId");
        this.cwd = requireText(cwd, "cwd");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
        this.lastEventUuid = Objects.requireNonNull(lastEventUuid, "lastEventUuid");
    }

    public SessionEvent message(ChatMessage message) {
        SessionEvent e = SessionEvent.message(nextUuid(), now(), sessionId, cwd, lastEventUuid, lastEventUuid, message);
        remember(e);
        return e;
    }

    public SessionEvent meta(MetaSessionEventDraft draft) {
        SessionEvent e = SessionEvent.meta(nextUuid(), now(), sessionId, cwd, lastEventUuid, lastEventUuid, draft);
        remember(e);
        return e;
    }

    public SessionEvent compactBoundary(CompactMetadata metadata) {
        SessionEvent e = SessionEvent.compactBoundary(nextUuid(), now(), sessionId, cwd, lastEventUuid, lastEventUuid, metadata);
        remember(e);
        return e;
    }

    private void remember(SessionEvent event) { lastEventUuid = Optional.of(event.uuid()); }

    private Instant now() { return Instant.now(clock); }

    private String nextUuid() { return requireText(uuidSupplier.get(), "uuid"); }

    private static String requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
