package code4j.context.accounting;

import code4j.core.message.*;
import code4j.model.ProviderUsage;
import code4j.model.UsageStaleness;

import java.util.List;
import java.util.Optional;

public final class TokenAccountingService {
    public TokenAccountingResult account(List<ChatMessage> messages) {
        int boundary = latestFreshProviderUsageBoundary(messages);
        if (boundary >= 0) {
            ProviderUsage usage = providerUsage(messages.get(boundary)).orElseThrow();
            long estimatedTail = estimate(messages.subList(boundary + 1, messages.size()));
            UsageBoundary usageBoundary = new UsageBoundary(boundary, messageBoundaryId(messages.get(boundary)));
            if (estimatedTail > 0) {
                return TokenAccountingResult.providerUsageWithEstimate(
                        usage.inputTokens() + estimatedTail, usage.outputTokens(),
                        usage.totalTokens() + estimatedTail, usage.totalTokens(), estimatedTail, usageBoundary);
            }
            return TokenAccountingResult.providerUsage(
                    usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), usageBoundary);
        }
        long estimate = estimate(messages);
        return TokenAccountingResult.estimateOnly(estimate, staleUsageReason(messages));
    }

    private int latestFreshProviderUsageBoundary(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            Optional<ProviderUsage> usage = providerUsage(m);
            if (usage.isPresent() && !usageStaleness(m).stale()) {
                return i;
            }
        }
        return -1;
    }

    private Optional<ProviderUsage> providerUsage(ChatMessage message) {
        return switch (message) {
            case AssistantMessage a -> a.providerUsage();
            case AssistantProgressMessage p -> p.providerUsage();
            case AssistantToolCallMessage t -> t.providerUsage();
            default -> Optional.empty();
        };
    }

    private UsageStaleness usageStaleness(ChatMessage message) {
        return switch (message) {
            case AssistantMessage a -> a.usageStaleness();
            case AssistantProgressMessage p -> p.usageStaleness();
            case AssistantToolCallMessage t -> t.usageStaleness();
            default -> UsageStaleness.fresh();
        };
    }

    private Optional<String> staleUsageReason(List<ChatMessage> messages) {
        for (ChatMessage m : messages) {
            Optional<ProviderUsage> usage = providerUsage(m);
            UsageStaleness staleness = usageStaleness(m);
            if (usage.isPresent() && staleness.stale()) {
                return staleness.reason().or(() -> Optional.of("provider usage was marked stale"));
            }
        }
        return Optional.empty();
    }

    private Optional<String> messageBoundaryId(ChatMessage message) {
        if (message instanceof AssistantToolCallMessage t) {
            return Optional.of(t.toolUseId());
        }
        return Optional.empty();
    }

    private long estimate(List<ChatMessage> messages) {
        if (messages.isEmpty()) return 0;
        long chars = 0;
        for (ChatMessage message : messages) {
            chars += switch (message) {
                case SystemMessage s -> s.content().length();
                case UserMessage u -> u.content().length();
                case AssistantMessage a -> a.content().length();
                case AssistantProgressMessage p -> p.content().length();
                case ToolResultMessage t -> t.content().length();
                case ContextSummaryMessage c -> c.content().length();
                case AssistantToolCallMessage t -> t.toolName().length() + t.input().toString().length();
                case AssistantThinkingMessage t -> t.blocks().toString().length();
                default -> 0;
            };
        }
        return Math.max(1, (chars + 3) / 4);
    }
}
