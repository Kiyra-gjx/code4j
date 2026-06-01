package code4j.context.boundary;

import code4j.core.message.AssistantToolCallMessage;
import code4j.core.message.ChatMessage;
import code4j.core.message.ToolResultMessage;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ContextBoundaryGuard {
    private ContextBoundaryGuard() {}

    public static boolean isCompactSafeBoundary(List<ChatMessage> messages) {
        List<ChatMessage> actual = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (actual.isEmpty()) return true;
        Set<String> openCalls = new HashSet<>();
        Set<String> completedCalls = new HashSet<>();
        for (ChatMessage message : actual) {
            if (message instanceof AssistantToolCallMessage tc) {
                if (openCalls.contains(tc.toolUseId()) || completedCalls.contains(tc.toolUseId())) return false;
                openCalls.add(tc.toolUseId());
            } else if (message instanceof ToolResultMessage tr) {
                if (!openCalls.remove(tr.toolUseId())) return false;
                if (!completedCalls.add(tr.toolUseId())) return false;
            }
        }
        return openCalls.isEmpty();
    }
}
