package code4j.skills;

import java.util.*;

public final class SkillRegistry {
    private final Map<String, LoadedSkill> skillsByName;

    public SkillRegistry(List<LoadedSkill> skills) {
        LinkedHashMap<String, LoadedSkill> byName = new LinkedHashMap<>();
        for (LoadedSkill s : Objects.requireNonNull(skills, "skills")) {
            byName.putIfAbsent(Objects.requireNonNull(s, "skill").name(), s);
        }
        this.skillsByName = Collections.unmodifiableMap(new LinkedHashMap<>(byName));
    }

    public List<SkillSummary> summaries() {
        return skillsByName.values().stream().map(LoadedSkill::summary).toList();
    }

    public Optional<LoadedSkill> load(String name) {
        if (name == null || name.trim().isEmpty()) return Optional.empty();
        return Optional.ofNullable(skillsByName.get(name.trim()));
    }
}
