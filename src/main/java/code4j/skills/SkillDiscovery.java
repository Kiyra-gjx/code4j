package code4j.skills;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class SkillDiscovery {
    private final Path appHome;
    private final Path cwd;
    private final Path osHome;

    public SkillDiscovery(Path appHome, Path cwd) {
        this(appHome, cwd, Path.of(System.getProperty("user.home")));
    }

    public SkillDiscovery(Path appHome, Path cwd, Path osHome) {
        this.appHome = Objects.requireNonNull(appHome, "appHome").toAbsolutePath().normalize();
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.osHome = Objects.requireNonNull(osHome, "osHome").toAbsolutePath().normalize();
    }

    public List<LoadedSkill> discover() {
        LinkedHashMap<String, LoadedSkill> byName = new LinkedHashMap<>();
        for (SkillRoot root : roots()) {
            for (LoadedSkill skill : listSkillDirs(root)) {
                byName.putIfAbsent(skill.name(), skill);
            }
        }
        return List.copyOf(byName.values());
    }

    private List<SkillRoot> roots() {
        return List.of(
                new SkillRoot(cwd.resolve(".code4j").resolve("skills"), SkillSource.PROJECT),
                new SkillRoot(appHome.resolve("skills"), SkillSource.USER),
                new SkillRoot(cwd.resolve(".claude").resolve("skills"), SkillSource.COMPAT),
                new SkillRoot(osHome.resolve(".claude").resolve("skills"), SkillSource.COMPAT)
        );
    }

    private List<LoadedSkill> listSkillDirs(SkillRoot root) {
        Path nr = root.path().toAbsolutePath().normalize();
        if (!Files.isDirectory(nr)) return List.of();
        List<LoadedSkill> results = new ArrayList<>();
        try (Stream<Path> paths = Files.list(nr)) {
            paths.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .forEach(p -> readSkill(nr, p, root.source(), results));
        } catch (IOException ignored) {}
        return results;
    }

    private void readSkill(Path root, Path skillDir, SkillSource source, List<LoadedSkill> results) {
        Path nsd = skillDir.toAbsolutePath().normalize();
        if (!nsd.startsWith(root)) return;
        Path skillPath = nsd.resolve("SKILL.md").toAbsolutePath().normalize();
        if (!skillPath.startsWith(root)) return;
        String name = nsd.getFileName().toString();
        if (name.isBlank()) return;
        try {
            String content = Files.readString(skillPath, StandardCharsets.UTF_8);
            results.add(new LoadedSkill(name, extractDescription(content), skillPath, source, content));
        } catch (IOException | RuntimeException ignored) {}
    }

    static String extractDescription(String markdown) {
        String n = Objects.requireNonNull(markdown, "markdown").replace("\r\n", "\n");
        int end = n.startsWith("---\n") ? n.indexOf("\n---\n", 4) : -1;
        if (end >= 0) {
            String fm = n.substring(4, end);
            for (String line : fm.split("\n")) {
                String l = line.trim();
                if (l.startsWith("description:")) {
                    String v = l.substring("description:".length()).trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))
                        v = v.substring(1, v.length() - 1).trim();
                    return v.replace("`", "");
                }
            }
            n = n.substring(end + "\n---\n".length());
        }
        for (String block : n.split("\n\n")) {
            String b = block.trim();
            if (b.isEmpty() || b.startsWith("#")) continue;
            for (String line : b.split("\n")) {
                String l = line.trim();
                if (!l.isEmpty() && !l.startsWith("#")) return l.replace("`", "");
            }
        }
        return "No description provided.";
    }

    private record SkillRoot(Path path, SkillSource source) {
        SkillRoot { path = Objects.requireNonNull(path, "path"); source = Objects.requireNonNull(source, "source"); }
    }
}
