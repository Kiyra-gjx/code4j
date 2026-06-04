package code4j.edit;

import code4j.permissions.model.PermissionResource.EditOperation;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * 描述一次待审查的文件修改。
 *
 * <p>EditReview 是文件写入前交给权限系统和用户审查的结构化摘要。
 * 它记录目标路径、操作类型、修改说明、diff 预览、修改前后规模、
 * 截断状态、审查指纹以及可选的完整 diff 引用。写入工具不应绕过
 * 该 review 直接修改文件。</p>
 */
public record EditReview(Path path, EditOperation operation, String summary, String diffPreview,
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
        if (reviewFingerprint.isBlank()) {
            throw new IllegalArgumentException("reviewFingerprint must not be blank");
        }
        diffRef = Objects.requireNonNull(diffRef, "diffRef");
    }
}
