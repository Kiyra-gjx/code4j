package code4j.tui;

import code4j.app.ApplicationServices;
import code4j.core.message.ChatMessage;
import code4j.core.message.UserMessage;
import code4j.core.turn.AgentTurnResult;
import code4j.core.turn.AgentTurnStopReason;
import code4j.session.plan.PersistenceAction;
import code4j.session.plan.TurnPersistencePlan;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MiniTui {
    public static final int DEFAULT_MAX_STEPS = 25;

    private final ApplicationServices services;
    private final BufferedReader input;
    private final PrintWriter output;
    private final int maxSteps;

    public MiniTui(ApplicationServices services, InputStream input, OutputStream output) {
        this(services, input, output, DEFAULT_MAX_STEPS);
    }

    public MiniTui(ApplicationServices services, InputStream input, OutputStream output, int maxSteps) {
        this.services = Objects.requireNonNull(services, "services");
        this.input = new BufferedReader(new InputStreamReader(Objects.requireNonNull(input, "input"), StandardCharsets.UTF_8));
        this.output = new PrintWriter(Objects.requireNonNull(output, "output"), true, StandardCharsets.UTF_8);
        if (maxSteps < 1) throw new IllegalArgumentException("maxSteps must be positive");
        this.maxSteps = maxSteps;
    }

    public void runLoop() {
        try {
            String line;
            while ((line = input.readLine()) != null) {
                if (!runLine(line)) return;
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private boolean runLine(String line) {
        if (line == null) return false;
        if (line.isBlank()) return true;
        String t = line.trim();
        if ("/exit".equalsIgnoreCase(t) || "/quit".equalsIgnoreCase(t)) return false;
        if ("/help".equalsIgnoreCase(t)) { output.println("/exit /help /compact /clear"); return true; }
        if ("/clear".equalsIgnoreCase(t)) { return true; }
        if ("/compact".equalsIgnoreCase(t)) {
            var result = services.manualCompact();
            output.println("[compact] " + result.status());
            return true;
        }

        UserMessage userMsg = new UserMessage(line);
        output.println("> " + userMsg.content());

        List<ChatMessage> history = new ArrayList<>(services.sessionMessages());
        history.add(userMsg);
        AgentTurnResult result = services.runTurn(services.turnRequest(history, maxSteps));
        renderResult(result);

        if (result.stopReason() == AgentTurnStopReason.AWAIT_USER) {
            try {
                output.print("> "); output.flush();
                String reply = input.readLine();
                if (reply != null) {
                    history = new ArrayList<>(result.messages());
                    history.add(new UserMessage(reply.trim()));
                    result = services.runTurn(services.turnRequest(history, maxSteps));
                    renderResult(result);
                }
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        return true;
    }

    private void renderResult(AgentTurnResult result) {
        if (result.stopReason() == AgentTurnStopReason.MODEL_ERROR) {
            output.println("[error] " + result.stopDetails().orElseThrow());
        } else if (result.stopReason() == AgentTurnStopReason.MAX_STEPS) {
            output.println("[max_steps] reached " + maxSteps);
        } else if (result.stopReason() == AgentTurnStopReason.CANCELLED) {
            output.println("[cancelled]");
        }
    }
}
