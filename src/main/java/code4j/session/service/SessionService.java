package code4j.session.service;

import code4j.core.message.ChatMessage;
import code4j.session.factory.SessionEventFactory;
import code4j.session.model.ForkDraft;
import code4j.session.model.ForkMetadata;
import code4j.session.model.RenameDraft;
import code4j.session.plan.PersistenceAction;
import code4j.session.plan.TurnPersistencePlan;
import code4j.session.runner.SessionPersistenceRunner;
import code4j.session.store.SessionMetadata;
import code4j.session.store.SessionStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class SessionService {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final int MAX_FORK_ATTEMPTS = 5;

    private final SessionStore store;
    private final Supplier<String> sessionIdSupplier;
    private final Clock clock;

    public SessionService(SessionStore store) {
        this(store, () -> UUID.randomUUID().toString(), Clock.systemUTC());
    }

    public SessionService(SessionStore store, Supplier<String> sessionIdSupplier, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.sessionIdSupplier = Objects.requireNonNull(sessionIdSupplier, "sessionIdSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<SessionMetadata> list(String cwd) {
        return store.listSessionsByCwd(requireText(cwd, "cwd"));
    }

    public void requireResumable(String cwd, String sessionId) {
        requireExisting(cwd, sessionId);
    }

    public List<ChatMessage> resumeMessages(String cwd, String sessionId) {
        requireExisting(cwd, sessionId);
        List<ChatMessage> messages = store.loadMessagesSinceLatestCompactBoundary(sessionId, cwd);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Session has no resumable messages: " + sessionId);
        }
        return messages;
    }

    public void rename(String cwd, String sessionId, String title) {
        String t = requireText(title, "title");
        requireExisting(cwd, sessionId);
        runner(cwd, sessionId).apply(new TurnPersistencePlan(List.of(
                new PersistenceAction.AppendSessionEventAction(new RenameDraft(t)))));
    }

    public String fork(String cwd, String sourceSessionId) {
        requireExisting(cwd, sourceSessionId);
        List<ChatMessage> messages = resumeMessages(cwd, sourceSessionId);
        String newId = allocateForkId(cwd);
        Optional<String> sourceEventUuid = store.latestEventUuid(sourceSessionId, cwd);
        SessionPersistenceRunner r = runner(cwd, newId);
        r.apply(new TurnPersistencePlan(List.of(
                new PersistenceAction.AppendSessionEventAction(new ForkDraft(new ForkMetadata(
                        sourceSessionId, sourceEventUuid, newId, cwd, Instant.now(clock)))),
                new PersistenceAction.AppendMessagesAction(messages))));
        return newId;
    }

    private SessionPersistenceRunner runner(String cwd, String sessionId) {
        return new SessionPersistenceRunner(store, new SessionEventFactory(
                sessionId, cwd, clock, () -> UUID.randomUUID().toString(),
                store.latestEventUuid(sessionId, cwd)));
    }

    private String allocateForkId(String cwd) {
        for (int i = 0; i < MAX_FORK_ATTEMPTS; i++) {
            String candidate = requireSessionId(sessionIdSupplier.get());
            if (store.readMetadata(candidate, cwd).isEmpty()) return candidate;
        }
        throw new IllegalStateException("Unable to allocate unique fork session id");
    }

    private void requireExisting(String cwd, String sessionId) {
        String c = requireText(cwd, "cwd");
        String sid = requireSessionId(sessionId);
        if (store.readMetadata(sid, c).isPresent()) return;
        List<String> others = store.findCwdsForSessionId(sid).stream()
                .filter(o -> !o.equals(c)).toList();
        if (!others.isEmpty()) {
            throw new IllegalArgumentException("Session " + sid + " belongs to cwd: " + others.getFirst());
        }
        throw new IllegalArgumentException("Session not found: " + sid);
    }

    private static String requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String requireSessionId(String id) {
        String v = requireText(id, "sessionId");
        if (!SESSION_ID_PATTERN.matcher(v).matches()) {
            throw new IllegalArgumentException("Invalid session id: " + v);
        }
        return v;
    }
}
