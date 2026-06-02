package code4j.app;

import code4j.config.RuntimeConfig;
import code4j.config.RuntimeConfigException;
import code4j.config.RuntimeConfigLoader;
import code4j.core.event.AgentEvent;
import code4j.core.event.AgentEventSink;
import code4j.core.message.*;
import code4j.core.turn.AgentTurnRequest;
import code4j.core.turn.AgentTurnResult;
import code4j.core.turn.AgentTurnStopReason;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Entry point for Code4j - a lightweight, local-first, terminal-first coding agent.
 */
public final class Code4jApp {
    private static final int DEFAULT_MAX_STEPS = 25;

    private Code4jApp() {}

    public static void main(String[] args) {
        Path home = Paths.get(System.getProperty("user.home"), ".code4j");
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

        RuntimeConfig config;
        try {
            config = RuntimeConfigLoader.load(home, cwd);
        } catch (RuntimeConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(2);
            return;
        }

        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        out.println("Code4j v0.1.0 — " + config.model());
        out.println("Provider: " + config.provider() + " | cwd: " + cwd);
        out.println("Type /help for commands, /exit to quit.");

        AgentEventSink eventSink = event -> {
            if (event instanceof AgentEvent.ToolStartedEvent e) {
                out.println("[tool] " + e.toolName() + " ...");
            } else if (event instanceof AgentEvent.ToolFinishedEvent e) {
                out.println("[tool] " + e.toolName() + (e.error() ? " ERROR" : " done"));
            } else if (event instanceof AgentEvent.AwaitUserEvent e) {
                out.println("[await] " + e.question());
            }
        };

        ApplicationServices services = ApplicationServices.create(home, cwd, config, eventSink);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            List<ChatMessage> history = new ArrayList<>();

            while (true) {
                out.print("> ");
                out.flush();
                String line = reader.readLine();
                if (line == null) break;

                String input = line.trim();
                if (input.isEmpty()) continue;

                if ("/exit".equals(input) || "/quit".equals(input)) break;
                if ("/help".equals(input)) {
                    out.println("Commands: /exit, /help, /clear");
                    continue;
                }
                if ("/clear".equals(input)) {
                    history.clear();
                    out.println("History cleared.");
                    continue;
                }

                history.add(new UserMessage(input));
                int prevSize = history.size();
                AgentTurnRequest request = services.turnRequest(history, DEFAULT_MAX_STEPS);
                AgentTurnResult result = services.runTurn(request);
                history = new ArrayList<>(result.messages());

                // Print assistant messages from this turn
                for (int i = prevSize + 1; i < history.size(); i++) {
                    if (history.get(i) instanceof AssistantMessage am) {
                        out.println(am.content());
                    }
                }

                if (result.stopReason() == AgentTurnStopReason.AWAIT_USER) {
                    out.print("> ");
                    out.flush();
                    String userReply = reader.readLine();
                    if (userReply == null) break;
                    history.add(new UserMessage(userReply.trim()));
                    request = services.turnRequest(history, DEFAULT_MAX_STEPS);
                    result = services.runTurn(request);
                    history = new ArrayList<>(result.messages());
                    for (ChatMessage msg : result.messages()) {
                        if (msg instanceof AssistantMessage am) out.println(am.content());
                    }
                }

                if (result.stopReason() != AgentTurnStopReason.FINAL
                        && result.stopReason() != AgentTurnStopReason.AWAIT_USER) {
                    out.println("[turn ended: " + result.stopReason() + "]");
                }
            }
        } catch (Exception e) {
            out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
