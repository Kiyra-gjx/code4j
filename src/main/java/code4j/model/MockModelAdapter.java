package code4j.model;

import code4j.core.loop.ModelAdapter;
import code4j.core.message.ChatMessage;
import code4j.core.step.*;
import code4j.tools.api.ToolCall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A ModelAdapter that returns pre-programmed steps. Used in tests.
 */
public final class MockModelAdapter implements ModelAdapter {
    private final List<AgentStep> steps;
    private int index;

    public MockModelAdapter(String finalContent) {
        this(List.of(new AssistantStep(finalContent, AssistantKind.FINAL)));
    }

    public MockModelAdapter(List<AgentStep> steps) {
        if (Objects.requireNonNull(steps, "steps").isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        this.steps = List.copyOf(steps);
    }

    public static MockModelAdapter toolThenFinal(String toolUseId, String toolName, String finalContent) {
        JsonNode input = "ask_user".equals(toolName)
                ? JsonNodeFactory.instance.objectNode().put("question", "Mock question?")
                : JsonNodeFactory.instance.objectNode();
        return toolThenFinal(new ToolCall(toolUseId, toolName, input), finalContent);
    }

    public static MockModelAdapter toolThenFinal(ToolCall call, String finalContent) {
        return new MockModelAdapter(List.of(
                new ToolCallsStep(List.of(call), Optional.empty(), ContentKind.UNSPECIFIED,
                        List.of(), Optional.empty(), Optional.empty()),
                new AssistantStep(finalContent, AssistantKind.FINAL)));
    }

    @Override
    public AgentStep next(List<ChatMessage> messages) {
        if (index >= steps.size()) return steps.getLast();
        return steps.get(index++);
    }
}
