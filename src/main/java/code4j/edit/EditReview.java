package code4j.edit;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record EditReview(Path path, String operation, String summary, String diffPreview,
                         long beforeChars, long afterChars, boolean beforeExists,
                         boolean truncated, String reviewFingerprint, Optional<String> diffRef) {
    public EditReview {
        path = Objects.requireNonNull(path, "path");
        operation = Objects.requireNonNull(operation, "operation");
        if (Objects.requireNonNull(summary, "summary").isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        diffPreview = Objects.requireNonNull(diffPreview, "diffPreview").replace("\r\n", "\n").replace('\r', '\n');
        if (beforeChars < 0 || afterChars < 0) {
            throw new IllegalArgumentException("char counts must be non-negative");
        }
        reviewFingerprint = Objects.requireNonNull(reviewFingerprint, "reviewFingerprint");
        diffRef = Objects.requireNonNull(diffRef, "diffRef");
    }
}
