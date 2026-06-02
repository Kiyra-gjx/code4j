package code4j.skills;

import java.nio.file.Path;
import java.util.Objects;

public record SkillSummary(String name, String description, Path path, SkillSource source) {
    public SkillSummary {
        name = requireText(name, "name");
        description = Objects.requireNonNull(description, "description");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        source = Objects.requireNonNull(source, "source");
    }

    private static String requireText(String v, String f) {
        if (Objects.requireNonNull(v, f).isBlank()) throw new IllegalArgumentException(f + " must not be blank");
        return v;
    }
}
