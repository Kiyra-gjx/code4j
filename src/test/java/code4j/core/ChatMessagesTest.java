package code4j.core;

import code4j.core.message.*;
import code4j.model.ProviderUsage;
import code4j.model.UsageStaleness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessagesTest {
    @Test
    void providerUsageCanOnlyAttachToAssistantSideMessages() {
        ProviderUsage usage = new ProviderUsage(10, 20, 30);

        ChatMessage assistant = ChatMessages.withProviderUsage(new AssistantMessage("ok"), usage);
        ChatMessage user = ChatMessages.withProviderUsage(new UserMessage("hi"), usage);

        assertEquals(usage, ((AssistantMessage) assistant).providerUsage().orElseThrow());
        assertInstanceOf(UserMessage.class, user);
    }

    @Test
    void markUsageStaleOnlyChangesAssistantSideMessages() {
        AssistantMessage assistant = new AssistantMessage("ok");

        AssistantMessage stale = (AssistantMessage) ChatMessages.markUsageStale(assistant, "compact boundary");

        assertEquals(UsageStaleness.stale("compact boundary"), stale.usageStaleness());
        assertInstanceOf(ToolResultMessage.class, ChatMessages.markUsageStale(new ToolResultMessage("id", "tool", "content", false), "ignored"));
    }
}
