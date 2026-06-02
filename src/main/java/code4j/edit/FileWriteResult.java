package code4j.edit;

import java.util.Objects;
import java.util.Optional;

public record FileWriteResult(boolean noOp, Optional<String> operation, String message) {
    public FileWriteResult {
        operation = Objects.requireNonNull(operation, "operation");
        if (noOp && operation.isPresent()) {
            throw new IllegalArgumentException("no-op result cannot carry an operation");
        }
        if (!noOp && operation.isEmpty()) {
            throw new IllegalArgumentException("applied result must carry an operation");
        }
        if (Objects.requireNonNull(message, "message").isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public static FileWriteResult noOp(String message) { return new FileWriteResult(true, Optional.empty(), message); }
    public static FileWriteResult applied(String operation, String message) { return new FileWriteResult(false, Optional.of(operation), message); }
}
