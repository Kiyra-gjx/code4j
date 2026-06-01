package code4j.core.loop;

import code4j.core.message.ChatMessage;
import code4j.core.step.AgentStep;

import java.util.List;

/**
 * Abstraction over a model provider. Takes a list of messages and returns
 * the next agent step — either text or tool calls.
 */
@FunctionalInterface
public interface ModelAdapter {
    AgentStep next(List<ChatMessage> messages);
}
