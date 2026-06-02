package code4j.session.runner;

import code4j.session.factory.SessionEventFactory;
import code4j.session.plan.PersistenceAction;
import code4j.session.plan.TurnPersistencePlan;
import code4j.session.store.SessionStore;

import java.util.Objects;

/**
 * Applies a TurnPersistencePlan to a SessionStore, converting each PersistenceAction
 * into one or more SessionEvents and appending them.
 */
public final class SessionPersistenceRunner {
    private final SessionStore store;
    private final SessionEventFactory factory;

    public SessionPersistenceRunner(SessionStore store, SessionEventFactory factory) {
        this.store = Objects.requireNonNull(store, "store");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public void apply(TurnPersistencePlan plan) {
        for (PersistenceAction action : Objects.requireNonNull(plan, "plan").actions()) {
            switch (action) {
                case PersistenceAction.AppendMessagesAction a -> {
                    for (var msg : a.messages()) store.append(factory.message(msg));
                }
                case PersistenceAction.AppendCompactBoundaryAction a -> {
                    store.append(factory.compactBoundary(a.metadata()));
                    store.append(factory.message(a.summaryMessage()));
                }
                case PersistenceAction.AppendSessionEventAction a ->
                        store.append(factory.meta(a.draft()));
            }
        }
    }
}
