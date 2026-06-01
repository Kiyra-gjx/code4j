package code4j.tools.validation;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Entry point for tool input validation.
 */
public final class ToolInputValidation {
    private ToolInputValidation() {
    }

    public static ToolInputValidator object(JsonNode input) {
        return new ToolInputValidator(input);
    }
}
