package code4j.tui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ToolInputSummarizer {
    private static final int MAX_FIELD_CHARS = 120;
    private static final int MAX_TOTAL_CHARS = 240;
    private static final String REDACTED = "<redacted>";
    private static final List<Pattern> SENSITIVE = List.of(
            Pattern.compile("(?i)(ANTHROPIC_AUTH_TOKEN\\s*=\\s*)('|\")?[^\\s;,&\"']+('|\")?"),
            Pattern.compile("(?i)(Authorization\\s*:\\s*Bearer\\s+)[^\\s;,&\"']+"));

    private ToolInputSummarizer() {}

    public static String summarize(String toolName, JsonNode input) {
        String summary = switch (toolName) {
            case "read_file" -> join(f("path", text(input, "path")), f("lineStart", val(input, "lineStart")));
            case "list_files" -> join(f("path", text(input, "path")));
            case "grep_files" -> join(f("path", text(input, "path")), f("pattern", text(input, "pattern")));
            case "write_file" -> join(f("path", text(input, "path")));
            case "run_command" -> f("cmd", text(input, "command"));
            default -> toolName.startsWith("mcp__") ? "mcp:" + toolName : oneLine(input.toString());
        };
        return truncate(redact(summary), MAX_TOTAL_CHARS);
    }

    private static String text(JsonNode input, String field) { JsonNode n = input.get(field); return n == null || n.isNull() ? "" : n.isTextual() ? n.asText() : n.toString(); }
    private static String val(JsonNode input, String field) { JsonNode n = input.get(field); return n == null || n.isNull() ? "" : n.asText(); }
    private static String f(String name, String value) { if (value == null || value.isBlank()) return ""; return name + "=" + truncate(redact(oneLine(value)), MAX_FIELD_CHARS); }
    private static String join(String... parts) { List<String> p = new ArrayList<>(); for (String s : parts) if (s != null && !s.isBlank()) p.add(s); return String.join(" ", p); }
    private static String oneLine(String v) { return v.replaceAll("\\s+", " ").trim(); }
    private static String truncate(String v, int max) { if (v.length() <= max) return v; if (max <= 3) return v.substring(0, max); return v.substring(0, max - 3) + "..."; }
    private static String redact(String v) { for (Pattern p : SENSITIVE) v = p.matcher(v).replaceAll("$1" + REDACTED); return v; }
}
