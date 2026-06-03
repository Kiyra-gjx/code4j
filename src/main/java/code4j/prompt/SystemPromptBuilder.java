package code4j.prompt;

import code4j.skills.SkillSummary;
import code4j.tools.api.Tool;
import code4j.tools.registry.ToolRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Builds the system prompt that defines the agent's behavior, available tools,
 * and operational rules. Injected as the first SystemMessage in every turn.
 */
public final class SystemPromptBuilder {

    public record Input(Path home, Path cwd, ToolRegistry tools, List<SkillSummary> skills) {
        public Input {
            home = Objects.requireNonNull(home, "home").toAbsolutePath().normalize();
            cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
            tools = Objects.requireNonNull(tools, "tools");
            skills = List.copyOf(Objects.requireNonNull(skills, "skills"));
        }
    }

    public String build(Input input) {
        Objects.requireNonNull(input, "input");
        StringJoiner prompt = new StringJoiner("\n\n");
        prompt.add("You are Code4j, a terminal coding assistant. "
                + "Inspect repositories, use tools, make code changes, and explain results clearly. "
                + "Prefer reading files, searching code, editing files, and running verification commands "
                + "over giving purely theoretical advice.");
        prompt.add("Current cwd: " + input.cwd());
        prompt.add("""
                When making code changes, keep them minimal, practical, and working-oriented.
                If the user asks you to build, modify, or generate something, do the work instead of stopping at a plan.
                Request multiple independent tool calls in the same step.
                Keep dependent actions sequential when later calls need earlier results.
                """);
        prompt.add(toolSection(input.tools()));
        if (!input.skills().isEmpty()) {
            prompt.add(skillSection(input.skills()));
        }
        prompt.add("""
                Structured response protocol:
                - Use <final>...</final> only when the task is complete and control returns to the user.
                - Use <progress>...</progress> for brief status updates during multi-step work.
                - After <progress>, continue immediately in the next step.
                - Plain text without tags may be treated as a completed response.
                """);
        prompt.add("""
                When using read_file, pay attention to the header fields.
                If TRUNCATED: yes, continue reading with a larger offset.
                """);
        maybeRead(input.home().resolve("AGENTS.md"), "Global instructions", prompt);
        maybeRead(input.cwd().resolve("AGENTS.md"), "Project instructions", prompt);
        return prompt.toString();
    }

    private String skillSection(List<SkillSummary> skills) {
        StringBuilder sb = new StringBuilder("Available skills (use load_skill to load full content):");
        for (SkillSummary s : skills) {
            sb.append("\n- ").append(s.name()).append(": ").append(s.description())
                    .append(" (source: ").append(s.source().label()).append(")");
        }
        return sb.toString();
    }

    private String toolSection(ToolRegistry registry) {
        StringBuilder sb = new StringBuilder("Available tools:");
        if (registry.list().isEmpty()) {
            sb.append("\n- none");
            return sb.toString();
        }
        for (Tool tool : registry.list()) {
            sb.append("\n- ").append(tool.metadata().name())
                    .append(": ").append(tool.metadata().description())
                    .append("\n  schema: ").append(tool.inputSchema().toString());
        }
        return sb.toString();
    }

    private void maybeRead(Path path, String label, StringJoiner prompt) {
        if (!Files.exists(path)) return;
        try {
            prompt.add(label + " from " + path + ":\n" + Files.readString(path));
        } catch (IOException e) {
            prompt.add(label + " from " + path + " could not be read: " + e.getMessage());
        }
    }
}
