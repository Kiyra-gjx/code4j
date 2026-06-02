package code4j.skills;

public enum SkillSource {
    PROJECT("project"),
    USER("user"),
    COMPAT("compat");

    private final String label;
    SkillSource(String label) { this.label = label; }
    public String label() { return label; }
}
