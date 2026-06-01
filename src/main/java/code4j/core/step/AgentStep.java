package code4j.core.step;

/**
 * One "step" in an agent turn — a single model API call and its response.
 * <p>
 * Each step is either a text response ({@link AssistantStep}) or a tool
 * invocation request ({@link ToolCallsStep}). The agent loop processes
 * steps sequentially until a stop condition is met.
 */
public sealed interface AgentStep permits AssistantStep, ToolCallsStep {
}
