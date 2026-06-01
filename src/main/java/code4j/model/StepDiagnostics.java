package code4j.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Diagnostics from a single model API call within a step.
 * Captures the model's stop reason and which content block types
 * were processed or ignored — useful for debugging unusual responses.
 */
public record StepDiagnostics(Optional<String> stopReason, List<String> blockTypes,
                              List<String> ignoredBlockTypes) {
    public StepDiagnostics {
        stopReason = Objects.requireNonNull(stopReason, "stopReason");
        blockTypes = List.copyOf(Objects.requireNonNull(blockTypes, "blockTypes"));
        ignoredBlockTypes = List.copyOf(Objects.requireNonNull(ignoredBlockTypes, "ignoredBlockTypes"));
    }

    public static StepDiagnostics empty() {
        return new StepDiagnostics(Optional.empty(), List.of(), List.of());
    }
}
