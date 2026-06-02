package code4j.tui;

import code4j.core.event.AgentEvent;
import code4j.core.event.AgentEventSink;
import code4j.core.message.AssistantMessage;
import code4j.core.message.AssistantProgressMessage;
import code4j.core.message.ToolResultMessage;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public final class MiniTuiEventSink implements AgentEventSink {
    private final PrintWriter out;

    public MiniTuiEventSink(OutputStream output) {
        this.out = new PrintWriter(Objects.requireNonNull(output, "output"), true, StandardCharsets.UTF_8);
    }

    @Override
    public void onEvent(AgentEvent event) {
        switch (event) {
            case AgentEvent.AssistantMessageEvent e -> {
                if (e.message() instanceof AssistantMessage am) out.println(am.content());
                else if (e.message() instanceof AssistantProgressMessage pm) out.println("[progress] " + pm.content());
            }
            case AgentEvent.ToolStartedEvent e -> {
                String s = ToolInputSummarizer.summarize(e.toolName(), e.input());
                out.println("[tool] " + e.toolName() + (s.isBlank() ? "" : " " + s));
            }
            case AgentEvent.ToolFinishedEvent e ->
                    out.println("[tool] " + e.toolName() + (e.error() ? " ERROR" : " done"));
            case AgentEvent.AwaitUserEvent e ->
                    out.println("[await] " + oneLine(e.question()));
            case AgentEvent.TurnCancelledEvent e ->
                    out.println("[cancelled] " + e.cancellation().reason());
            case AgentEvent.AutoCompactEvent e -> {
                if (e.type() == code4j.context.compact.AutoCompactEventType.COMPLETED)
                    out.println("[compact] done");
            }
            case AgentEvent.ContextStatsEvent e -> {}
            default -> {}
        }

        // Show tool errors inline
        if (event instanceof AgentEvent.AssistantMessageEvent e
                && e.message() instanceof ToolResultMessage trm && trm.error()) {
            out.println("[tool_error] " + oneLine(trm.content()));
            permissionFeedback(trm.content()).ifPresent(f -> out.println("[deny] " + f));
        }
    }

    private static String oneLine(String v) { return v.replaceAll("\\s+", " ").trim(); }
    private static Optional<String> permissionFeedback(String content) {
        String prefix = "Permission denied:";
        String c = oneLine(content);
        if (!c.startsWith(prefix)) return Optional.empty();
        String fb = c.substring(prefix.length()).trim();
        return fb.isBlank() ? Optional.empty() : Optional.of(fb);
    }
}
