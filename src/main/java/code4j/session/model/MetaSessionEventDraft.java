package code4j.session.model;

public sealed interface MetaSessionEventDraft permits RenameDraft, ForkDraft {
    SessionEventType eventType();
}
