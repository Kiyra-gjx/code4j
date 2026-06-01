package code4j.tools.result;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Reference to a tool result that was too large and was stored on disk instead of in memory.
 */
public record ToolResultStorageRef(String id, Path path, long bytes) {
    public ToolResultStorageRef {
        if (Objects.requireNonNull(id, "id").isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        path = Objects.requireNonNull(path, "path");
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must be non-negative");
        }
    }
}
