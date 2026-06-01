package code4j.core.step;

/** Whether a model step is the final response or a streaming progress delta. */
public enum AssistantKind {
    FINAL,
    PROGRESS,
    UNSPECIFIED
}
