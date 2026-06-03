package code4j.context.compact;

import code4j.context.stats.ContextStats;
import code4j.core.message.*;

import java.util.*;

/**
 * Deterministic middle-layer compression that removes intermediate tool results
 * while preserving important rounds (file edits, errors, recent context).
 * Triggered at ~65% utilization — between microcompact (50%) and autoCompact (~85%).
 */
public final class SnipCompactService {
    private static final double TRIGGER_UTILIZATION = 0.65;
    private static final int KEEP_RECENT_ROUNDS = 5;
    private static final Set<String> FILE_MODIFY_TOOLS = Set.of("write_file", "edit_file", "modify_file", "patch_file");
    private static final Set<String> CLEARABLE_TOOLS = Set.of("read_file", "list_files", "grep_files", "run_command", "web_search", "web_fetch");
    private static final String CLEARED_PLACEHOLDER = "[Output cleared for context space by snip compact.]";

    private final int keepRecentRounds;

    public SnipCompactService() {
        this(KEEP_RECENT_ROUNDS);
    }

    public SnipCompactService(int keepRecentRounds) {
        if (keepRecentRounds < 1) throw new IllegalArgumentException("keepRecentRounds must be positive");
        this.keepRecentRounds = keepRecentRounds;
    }

    public boolean shouldCompact(ContextStats stats) {
        return stats.utilization() >= TRIGGER_UTILIZATION;
    }

    public SnipCompactResult compact(List<ChatMessage> messages) {
        List<Round> rounds = extractRounds(messages);
        if (rounds.isEmpty()) return new SnipCompactResult(messages, 0);

        int clearedCount = 0;
        int totalRounds = rounds.size();

        for (int i = 0; i < totalRounds; i++) {
            Round round = rounds.get(i);
            boolean isRecent = i >= totalRounds - keepRecentRounds;
            if (isRecent || shouldPreserve(round)) continue;
            for (int idx : round.toolResultIndices) {
                ChatMessage msg = messages.get(idx);
                if (msg instanceof ToolResultMessage trm) {
                    if (CLEARABLE_TOOLS.contains(trm.toolName()) && !trm.content().equals(CLEARED_PLACEHOLDER)) {
                        clearedCount++;
                    }
                }
            }
        }

        if (clearedCount == 0) return new SnipCompactResult(List.copyOf(messages), 0);

        List<ChatMessage> compacted = new ArrayList<>(messages.size());
        Set<Integer> clearIndices = new HashSet<>();

        for (int i = 0; i < totalRounds; i++) {
            Round round = rounds.get(i);
            boolean isRecent = i >= totalRounds - keepRecentRounds;
            if (!isRecent && !shouldPreserve(round)) {
                for (int idx : round.toolResultIndices) {
                    if (messages.get(idx) instanceof ToolResultMessage trm && CLEARABLE_TOOLS.contains(trm.toolName())) {
                        clearIndices.add(idx);
                    }
                }
            }
        }

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (clearIndices.contains(i) && msg instanceof ToolResultMessage trm) {
                compacted.add(new ToolResultMessage(trm.toolUseId(), trm.toolName(), CLEARED_PLACEHOLDER, trm.error()));
            } else {
                compacted.add(msg);
            }
        }

        for (int i = 0; i < compacted.size(); i++) {
            ChatMessage msg = compacted.get(i);
            if (msg instanceof AssistantMessage || msg instanceof AssistantProgressMessage
                    || msg instanceof AssistantToolCallMessage) {
                compacted.set(i, ChatMessages.markUsageStale(msg, "snip compact"));
            }
        }

        return new SnipCompactResult(List.copyOf(compacted), clearedCount);
    }

    private boolean shouldPreserve(Round round) {
        for (int idx : round.toolResultIndices) {
            ChatMessage msg = round.messages.get(idx);
            if (msg instanceof ToolResultMessage trm) {
                if (trm.error()) return true;
                if (FILE_MODIFY_TOOLS.contains(trm.toolName())) return true;
            }
        }
        return false;
    }

    /**
     * Extracts conversation rounds. A round starts with a UserMessage
     * and includes everything until the next UserMessage.
     */
    static List<Round> extractRounds(List<ChatMessage> messages) {
        List<Round> rounds = new ArrayList<>();
        int roundStart = -1;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof SystemMessage) continue;
            if (msg instanceof ContextSummaryMessage) continue;
            if (msg instanceof UserMessage) {
                if (roundStart >= 0) {
                    rounds.add(buildRound(messages, roundStart, i));
                }
                roundStart = i;
            }
        }
        if (roundStart >= 0 && roundStart < messages.size()) {
            rounds.add(buildRound(messages, roundStart, messages.size()));
        }
        return rounds;
    }

    private static Round buildRound(List<ChatMessage> messages, int start, int end) {
        List<Integer> toolResultIndices = new ArrayList<>();
        for (int i = start; i < end; i++) {
            if (messages.get(i) instanceof ToolResultMessage) {
                toolResultIndices.add(i);
            }
        }
        return new Round(start, end, messages, toolResultIndices);
    }

    record Round(int startIndex, int endIndex, List<ChatMessage> messages, List<Integer> toolResultIndices) {}

    public record SnipCompactResult(List<ChatMessage> messages, int clearedCount) {
        public SnipCompactResult {
            messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        }
        public boolean compacted() { return clearedCount > 0; }
    }
}
