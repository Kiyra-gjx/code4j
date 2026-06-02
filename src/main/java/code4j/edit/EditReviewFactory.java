package code4j.edit;

import code4j.permissions.model.PermissionResource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class EditReviewFactory {
    private EditReviewFactory() {}

    public static PermissionResource.EditResource review(Path path, String operation, String summary,
                                                          Optional<String> beforeContent, String afterContent,
                                                          Optional<String> toolUseId) {
        EditReview review = UnifiedDiffBuilder.build(path, operation, summary, beforeContent, afterContent);
        return new PermissionResource.EditResource(path, summary, review.diffPreview());
    }
}
