package code4j.tui;

import code4j.permissions.api.PermissionPromptHandler;
import code4j.permissions.model.PermissionChoice;
import code4j.permissions.model.PermissionDecision;
import code4j.permissions.model.PermissionPromptResult;
import code4j.permissions.model.PermissionRequest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ConsolePermissionPromptHandler implements PermissionPromptHandler {
    private final BufferedReader input;
    private final PrintWriter output;

    public ConsolePermissionPromptHandler(InputStream input, OutputStream output) {
        this(new BufferedReader(new InputStreamReader(Objects.requireNonNull(input, "input"), StandardCharsets.UTF_8)), output);
    }

    public ConsolePermissionPromptHandler(BufferedReader input, OutputStream output) {
        this.input = Objects.requireNonNull(input, "input");
        this.output = new PrintWriter(Objects.requireNonNull(output, "output"), true, StandardCharsets.UTF_8);
    }

    @Override
    public PermissionPromptResult prompt(PermissionRequest request) {
        output.println("permission: " + request.details().title());
        output.println(request.details().body());
        for (String fact : request.details().facts()) output.println("  " + fact);
        for (int i = 0; i < request.choices().size(); i++) {
            PermissionChoice c = request.choices().get(i);
            output.println("  " + (i + 1) + ") " + c.label() + " [" + c.key() + "]");
        }
        try {
            PermissionChoice choice = readChoice(request);
            if (choice.requiresFeedback()) {
                output.print("Feedback: "); output.flush();
                String fb = input.readLine();
                if (fb == null || fb.isBlank()) fb = "Permission denied";
                return PermissionPromptResult.deny(choice.decision(), fb);
            }
            if (isAllow(choice.decision())) return PermissionPromptResult.allow(choice.decision());
            return PermissionPromptResult.deny(choice.decision(), null);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private PermissionChoice readChoice(PermissionRequest request) throws IOException {
        while (true) {
            output.print("Choice: "); output.flush();
            String line = input.readLine();
            if (line == null) return fallbackDeny(request);
            Optional<PermissionChoice> sel = select(request, line);
            if (sel.isPresent()) return sel.orElseThrow();
            output.println("Unknown choice: " + line.trim());
        }
    }

    private static Optional<PermissionChoice> select(PermissionRequest request, String line) {
        if (line == null) return Optional.empty();
        String n = line.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (n.startsWith("[") && n.endsWith("]")) n = n.substring(1, n.length() - 1).trim();
        try { int idx = Integer.parseInt(n); if (idx >= 1 && idx <= request.choices().size()) return Optional.of(request.choices().get(idx - 1)); }
        catch (NumberFormatException ignored) {}
        for (PermissionChoice c : request.choices()) { if (c.key().toLowerCase(Locale.ROOT).equals(n) || c.label().toLowerCase(Locale.ROOT).equals(n)) return Optional.of(c); }
        return Optional.empty();
    }

    private static PermissionChoice fallbackDeny(PermissionRequest r) {
        return r.choices().stream().filter(c -> c.decision() == PermissionDecision.DENY_ONCE).findFirst()
                .orElseGet(() -> r.choices().getLast());
    }

    private static boolean isAllow(PermissionDecision d) { return d == PermissionDecision.ALLOW_ONCE || d == PermissionDecision.ALLOW_TURN || d == PermissionDecision.ALLOW_ALWAYS; }
}
