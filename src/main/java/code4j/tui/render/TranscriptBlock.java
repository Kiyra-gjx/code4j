package code4j.tui.render;

import java.util.Objects;
import java.util.Optional;

public record TranscriptBlock(Kind kind, String id, String text, Optional<String> toolUseId,
                              Optional<String> toolName, Optional<ToolStatus> toolStatus) {
    public TranscriptBlock {
        kind = Objects.requireNonNull(kind, "kind");
        if (Objects.requireNonNull(id, "id").isBlank()) throw new IllegalArgumentException("id must not be blank");
        text = Objects.requireNonNull(text, "text");
        toolUseId = Objects.requireNonNull(toolUseId, "toolUseId");
        toolName = Objects.requireNonNull(toolName, "toolName");
        toolStatus = Objects.requireNonNull(toolStatus, "toolStatus");
    }

    public TranscriptBlock(Kind kind, String id, String text) { this(kind, id, text, Optional.empty(), Optional.empty(), Optional.empty()); }
    public TranscriptBlock(Kind kind, String text) { this(kind, kind.name().toLowerCase() + ":" + text.hashCode(), text); }

    public static TranscriptBlock user(String text) { return new TranscriptBlock(Kind.USER, text); }
    public static TranscriptBlock assistant(String text) { return new TranscriptBlock(Kind.ASSISTANT, text); }
    public static TranscriptBlock progress(String text) { return new TranscriptBlock(Kind.PROGRESS, text); }
    public static TranscriptBlock toolStarted(String toolUseId, String toolName, String summary) {
        return new TranscriptBlock(Kind.TOOL, "tool:" + toolUseId, summary == null ? "" : summary, Optional.of(toolUseId), Optional.of(toolName), Optional.of(ToolStatus.RUNNING));
    }
    public static TranscriptBlock toolResult(String toolUseId, String toolName, boolean error, String output) {
        return new TranscriptBlock(Kind.TOOL, "tool:" + toolUseId, output, Optional.of(toolUseId), Optional.of(toolName), Optional.of(error ? ToolStatus.ERROR : ToolStatus.OK));
    }
    public static TranscriptBlock askUser(String toolUseId, String question) {
        return new TranscriptBlock(Kind.ASK_USER, "ask:" + toolUseId, question, Optional.of(toolUseId), Optional.of("ask_user"), Optional.empty());
    }
    public static TranscriptBlock diagnostic(String text) { return new TranscriptBlock(Kind.DIAGNOSTIC, text); }
    public static TranscriptBlock compact(String text) { return new TranscriptBlock(Kind.COMPACT, text); }
    public static TranscriptBlock permission(String requestId, String text) {
        return new TranscriptBlock(Kind.PERMISSION, "perm:" + requestId, text);
    }

    String renderText() {
        return switch (kind) {
            case USER -> "> " + text;
            case ASSISTANT -> text;
            case PROGRESS -> text;
            case TOOL -> toolName.map(n -> "[" + n + " " + toolStatus.map(Object::toString).orElse("") + "] " + text).orElse(text);
            case ASK_USER -> "[ask] " + text;
            case PERMISSION -> text;
            case DIAGNOSTIC -> text;
            case COMPACT -> "[compact] " + text;
        };
    }

    public enum Kind { USER, ASSISTANT, PROGRESS, TOOL, ASK_USER, PERMISSION, DIAGNOSTIC, COMPACT }
    public enum ToolStatus { RUNNING, OK, ERROR }
}
