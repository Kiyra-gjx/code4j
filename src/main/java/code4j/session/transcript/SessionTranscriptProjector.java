package code4j.session.transcript;

import code4j.core.message.*;
import code4j.session.model.SessionEvent;
import code4j.session.model.SessionEventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SessionTranscriptProjector {
    public List<TranscriptEntry> project(List<SessionEvent> events) {
        List<TranscriptEntry> entries = new ArrayList<>();
        for (SessionEvent event : events) {
            if (event.type() == SessionEventType.COMPACT_BOUNDARY) {
                entries.add(new TranscriptEntry(TranscriptEntry.Kind.COMPACT,
                        event.compactMetadata().map(cm -> "Compacted: " + cm.tokensBefore()
                                + " -> " + cm.tokensAfter() + " tokens").orElse("Compacted"),
                        Optional.empty(), Optional.empty()));
                continue;
            }
            event.message().ifPresent(m -> entries.add(projectMessage(m)));
        }
        return List.copyOf(entries);
    }

    private TranscriptEntry projectMessage(ChatMessage message) {
        return switch (message) {
            case UserMessage m -> new TranscriptEntry(TranscriptEntry.Kind.USER, m.content(), Optional.empty(), Optional.empty());
            case AssistantMessage m -> new TranscriptEntry(TranscriptEntry.Kind.ASSISTANT, m.content(), Optional.empty(), Optional.empty());
            case AssistantProgressMessage m -> new TranscriptEntry(TranscriptEntry.Kind.PROGRESS, m.content(), Optional.empty(), Optional.empty());
            case AssistantToolCallMessage m -> new TranscriptEntry(TranscriptEntry.Kind.TOOL, "tool_call " + m.toolUseId(), Optional.of(m.toolName()), Optional.empty());
            case ToolResultMessage m -> new TranscriptEntry(TranscriptEntry.Kind.TOOL, m.content(), Optional.of(m.toolName()), Optional.of(m.error()));
            case ContextSummaryMessage m -> new TranscriptEntry(TranscriptEntry.Kind.COMPACT, m.content(), Optional.empty(), Optional.empty());
            case AssistantThinkingMessage m -> new TranscriptEntry(TranscriptEntry.Kind.PROGRESS, "thinking blocks: " + m.blocks().size(), Optional.empty(), Optional.empty());
            case SystemMessage m -> new TranscriptEntry(TranscriptEntry.Kind.ASSISTANT, m.content(), Optional.empty(), Optional.empty());
            default -> throw new IllegalArgumentException("Unknown message type: " + message.getClass().getName());
        };
    }
}
