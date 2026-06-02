package code4j.mcp;

import code4j.tools.result.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class McpToolResultFormatter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpToolResultFormatter() {}

    public static ToolResult toToolResult(JsonNode result) {
        if (result == null || result.isMissingNode() || result.isNull()) return ToolResult.ok("null");
        List<String> parts = new ArrayList<>();
        JsonNode content = result.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode block : content) parts.add(formatBlock(block));
        }
        JsonNode sc = result.get("structuredContent");
        if (sc != null && !sc.isMissingNode() && !sc.isNull()) parts.add("STRUCTURED_CONTENT:\n" + pretty(sc));
        if (parts.isEmpty()) parts.add(pretty(result));
        String output = String.join("\n\n", parts).trim();
        return result.path("isError").asBoolean(false) ? ToolResult.error(output) : ToolResult.ok(output);
    }

    private static String formatBlock(JsonNode block) {
        if ("text".equals(block.path("type").asText()) && block.has("text")) return block.path("text").asText();
        return pretty(block);
    }

    private static String pretty(JsonNode node) {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node); }
        catch (Exception ignored) { return String.valueOf(node); }
    }
}
