package code4j.tools.builtin;

import code4j.skills.SkillRegistry;
import code4j.tools.api.Tool;
import code4j.tools.api.ToolContext;
import code4j.tools.api.ValidationResult;
import code4j.tools.metadata.*;
import code4j.tools.result.ToolResult;
import code4j.tools.validation.ToolInputValidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class LoadSkillTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createSchema();
    private static final ToolMetadata METADATA = new ToolMetadata("load_skill", "Load a discovered SKILL.md file to follow that workflow.", INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.READ), ToolStatus.AVAILABLE);

    private final SkillRegistry skillRegistry;

    public LoadSkillTool(SkillRegistry skillRegistry) { this.skillRegistry = Objects.requireNonNull(skillRegistry, "skillRegistry"); }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input).requiredString("name").custom((json, b) -> {
            JsonNode n = json == null ? null : json.get("name");
            if (n == null || !n.isTextual()) return;
            String name = n.asText().trim();
            if (name.isEmpty()) { b.addError("name must not be blank"); return; }
            if (name.contains("/") || name.contains("\\") || name.contains("..")) { b.addError("name must be a skill name, not a path"); return; }
            b.normalized().put("name", name);
        }).build();
    }

    @Override public ToolResult run(JsonNode input, ToolContext ctx) {
        String name = input.get("name").asText().trim();
        return skillRegistry.load(name).map(s -> ToolResult.ok("SKILL: " + s.name() + "\nSOURCE: " + s.source().label() + "\nPATH: " + s.path() + "\n\n" + s.content()))
                .orElseGet(() -> {
                    String avail = skillRegistry.summaries().stream().map(sum -> sum.name()).collect(Collectors.joining(", "));
                    return ToolResult.error("Unknown skill: " + name + (avail.isEmpty() ? "\nNo skills discovered." : "\nAvailable: " + avail));
                });
    }

    private static ObjectNode createSchema() { ObjectNode s = JSON.objectNode(); s.put("type", "object"); s.putObject("properties").putObject("name").put("type", "string"); s.putArray("required").add("name"); return s; }
}
