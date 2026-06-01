package code4j.context.manager;

import code4j.context.stats.ContextStats;
import code4j.core.message.*;
import code4j.model.UsageStaleness;
import code4j.tools.result.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class ContextManager {
    private static final String MICROCOMPACT_MARKER = "[Output cleared for context space.]";
    private static final double MICROCOMPACT_UTILIZATION = 0.50d;
    private static final int MICROCOMPACT_RETAIN_RECENT = 3;
    private static final Set<String> MICROCOMPACTABLE_TOOLS = Set.of("read_file", "run_command", "list_files", "grep_files");

    private final ToolResultStorage storage;
    private final int largeToolResultThreshold;
    private final int toolResultBatchBudget;
    private final int previewChars;
    private final boolean noOp;
    private final Map<ReplacementCacheKey, ToolResultReplacementRecord> replacementCache = new HashMap<>();

    public ContextManager(ToolResultStorage storage, int largeToolResultThreshold, int previewChars) {
        this(storage, largeToolResultThreshold, largeToolResultThreshold, previewChars);
    }

    public ContextManager(ToolResultStorage storage, int largeToolResultThreshold, int toolResultBatchBudget, int previewChars) {
        this.storage = Objects.requireNonNull(storage, "storage");
        if (largeToolResultThreshold < 0) throw new IllegalArgumentException("largeToolResultThreshold must be non-negative");
        if (toolResultBatchBudget < 0) throw new IllegalArgumentException("toolResultBatchBudget must be non-negative");
        if (previewChars < 0) throw new IllegalArgumentException("previewChars must be non-negative");
        this.largeToolResultThreshold = largeToolResultThreshold;
        this.toolResultBatchBudget = toolResultBatchBudget;
        this.previewChars = previewChars;
        this.noOp = false;
    }

    private ContextManager() {
        this.storage = null;
        this.largeToolResultThreshold = Integer.MAX_VALUE;
        this.toolResultBatchBudget = Integer.MAX_VALUE;
        this.previewChars = 0;
        this.noOp = true;
    }

    public static ContextManager noOp() { return new ContextManager(); }

    public ToolResultReplacementResult replaceLargeToolResult(ToolResultMessage message) {
        if (noOp) return new ToolResultReplacementResult(message, Optional.empty());
        String content = message.content();
        if (content.length() <= largeToolResultThreshold || content.startsWith("<persisted-output ")) {
            return new ToolResultReplacementResult(message, Optional.empty());
        }
        ToolResultReplacementRecord record = replacementFor(message, content, ToolResultReplacementTrigger.SINGLE_RESULT_TOO_LARGE);
        ToolResultMessage repl = new ToolResultMessage(message.toolUseId(), message.toolName(), record.replacementContent(), message.error());
        return new ToolResultReplacementResult(repl, Optional.of(record));
    }

    public ToolResultBudgetResult applyToolResultBudget(List<ToolResultMessage> results) {
        List<ToolResultMessage> r = List.copyOf(Objects.requireNonNull(results, "results"));
        if (noOp || r.isEmpty()) return new ToolResultBudgetResult(r, List.of());
        int totalChars = r.stream().mapToInt(m -> m.content().length()).sum();
        if (totalChars <= toolResultBatchBudget) return new ToolResultBudgetResult(r, List.of());
        List<ToolResultMessage> budgeted = new ArrayList<>(r);
        List<ToolResultReplacementRecord> replacements = new ArrayList<>();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < budgeted.size(); i++) {
            if (!budgeted.get(i).content().startsWith("<persisted-output ")) candidates.add(i);
        }
        candidates.sort(Comparator.comparingInt((Integer i) -> budgeted.get(i).content().length()).reversed());
        for (int i : candidates) {
            if (totalChars <= toolResultBatchBudget && !replacements.isEmpty()) break;
            ToolResultMessage orig = budgeted.get(i);
            ToolResultReplacementRecord rep = replacementFor(orig, orig.content(), ToolResultReplacementTrigger.BATCH_BUDGET_EXCEEDED);
            ToolResultMessage newMsg = new ToolResultMessage(orig.toolUseId(), orig.toolName(), rep.replacementContent(), orig.error());
            budgeted.set(i, newMsg);
            replacements.add(rep);
            totalChars = totalChars - orig.content().length() + newMsg.content().length();
        }
        return new ToolResultBudgetResult(budgeted, replacements);
    }

    public List<ChatMessage> microcompact(List<ChatMessage> messages) {
        return List.copyOf(Objects.requireNonNull(messages, "messages"));
    }

    public List<ChatMessage> microcompact(List<ChatMessage> messages, ContextStats stats) {
        List<ChatMessage> msgs = List.copyOf(Objects.requireNonNull(messages, "messages"));
        Objects.requireNonNull(stats, "stats");
        if (noOp || msgs.isEmpty() || stats.utilization() < MICROCOMPACT_UTILIZATION) return msgs;
        List<Integer> compactable = new ArrayList<>();
        for (int i = 0; i < msgs.size(); i++) {
            if (msgs.get(i) instanceof ToolResultMessage tr
                    && MICROCOMPACTABLE_TOOLS.contains(tr.toolName())
                    && !tr.content().startsWith("<persisted-output ")
                    && !MICROCOMPACT_MARKER.equals(tr.content())) {
                compactable.add(i);
            }
        }
        int clearCount = compactable.size() - MICROCOMPACT_RETAIN_RECENT;
        if (clearCount <= 0) return msgs;
        List<ChatMessage> compacted = new ArrayList<>(msgs);
        boolean changed = false;
        for (int i = 0; i < clearCount; i++) {
            int idx = compactable.get(i);
            ToolResultMessage orig = (ToolResultMessage) compacted.get(idx);
            compacted.set(idx, new ToolResultMessage(orig.toolUseId(), orig.toolName(), MICROCOMPACT_MARKER, orig.error()));
            changed = true;
        }
        return changed ? List.copyOf(markProviderUsageStale(compacted)) : msgs;
    }

    private List<ChatMessage> markProviderUsageStale(List<ChatMessage> messages) {
        String reason = "tool_result content was microcompacted";
        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) result.add(markStale(m, reason));
        return result;
    }

    private ChatMessage markStale(ChatMessage message, String reason) {
        UsageStaleness s = UsageStaleness.stale(reason);
        return switch (message) {
            case AssistantMessage a when a.providerUsage().isPresent() && !a.usageStaleness().stale() ->
                    new AssistantMessage(a.content(), a.providerUsage(), s);
            case AssistantProgressMessage p when p.providerUsage().isPresent() && !p.usageStaleness().stale() ->
                    new AssistantProgressMessage(p.content(), p.providerUsage(), s);
            case AssistantToolCallMessage t when t.providerUsage().isPresent() && !t.usageStaleness().stale() ->
                    new AssistantToolCallMessage(t.toolUseId(), t.toolName(), t.input(), t.providerUsage(), s);
            default -> message;
        };
    }

    private ToolResultReplacementRecord replacementFor(ToolResultMessage msg, String content, ToolResultReplacementTrigger trigger) {
        ReplacementCacheKey key = new ReplacementCacheKey(msg.toolUseId(), msg.toolName(), contentHash(content), trigger);
        ToolResultReplacementRecord cached = replacementCache.get(key);
        if (cached != null) return cached;
        ToolResultStorageRef ref = storage.store(content);
        String prev = content.substring(0, Math.min(previewChars, content.length()));
        String repl = "<persisted-output toolUseId=\"" + msg.toolUseId() + "\" toolName=\"" + msg.toolName() + "\">\n"
                + "STORAGE_REF: " + ref.id() + "\nPATH: " + ref.path() + "\nBYTES: " + ref.bytes()
                + "\nORIGINAL_CHARS: " + content.length() + "\nPREVIEW:\n" + prev + "\n</persisted-output>";
        ToolResultReplacementRecord record = new ToolResultReplacementRecord(
                msg.toolUseId(), msg.toolName(), trigger, ref, repl, prev, content.length(), prev.length(), repl.length());
        replacementCache.put(key, record);
        return record;
    }

    private static String contentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record ReplacementCacheKey(String toolUseId, String toolName, String contentHash, ToolResultReplacementTrigger trigger) {
        ReplacementCacheKey {
            Objects.requireNonNull(toolUseId, "toolUseId");
            Objects.requireNonNull(toolName, "toolName");
            Objects.requireNonNull(contentHash, "contentHash");
            Objects.requireNonNull(trigger, "trigger");
        }
    }
}
