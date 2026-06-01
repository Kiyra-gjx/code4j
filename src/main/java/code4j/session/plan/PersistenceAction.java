package code4j.session.plan;

import code4j.context.compact.CompactMetadata;
import code4j.core.message.ChatMessage;
import code4j.session.model.MetaSessionEventDraft;

import java.util.List;
import java.util.Objects;

/**
 * A single persistence operation to be executed after a turn completes.
 * This is a sealed interface so the compiler knows all possible action types.
 */
public sealed interface PersistenceAction
        permits PersistenceAction.AppendMessagesAction,
        PersistenceAction.AppendCompactBoundaryAction,
        PersistenceAction.AppendSessionEventAction {

    /** Append one or more messages to the session transcript. */
    record AppendMessagesAction(List<ChatMessage> messages) implements PersistenceAction {
        public AppendMessagesAction {
            messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
            if (messages.isEmpty()) {
                throw new IllegalArgumentException("append messages action requires at least one message");
            }
        }
    }

    /** Record a context compaction boundary in the session. */
    record AppendCompactBoundaryAction(ChatMessage summaryMessage, CompactMetadata metadata)
            implements PersistenceAction {
        public AppendCompactBoundaryAction {
            summaryMessage = Objects.requireNonNull(summaryMessage, "summaryMessage");
            metadata = Objects.requireNonNull(metadata, "metadata");
        }
    }

    /** Record a session lifecycle event (rename, fork, etc.). */
    record AppendSessionEventAction(MetaSessionEventDraft draft) implements PersistenceAction {
        public AppendSessionEventAction {
            draft = Objects.requireNonNull(draft, "draft");
        }
    }
}
