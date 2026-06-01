package code4j.tools.result;

import code4j.core.message.ToolResultMessage;

import java.util.List;
import java.util.Objects;

/**
 * The result of applying a context budget to tool results —
 * some may have been replaced with shorter versions.
 */
public record ToolResultBudgetResult(List<ToolResultMessage> results,
                                     List<ToolResultReplacementRecord> replacements) {
    public ToolResultBudgetResult {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        replacements = List.copyOf(Objects.requireNonNull(replacements, "replacements"));
    }
}
