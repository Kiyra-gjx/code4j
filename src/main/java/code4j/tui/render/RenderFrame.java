package code4j.tui.render;

import java.util.List;
import java.util.Objects;

public record RenderFrame(List<String> lines, int cursorRow, int cursorColumn) {
    public RenderFrame {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }
    public String text() { return String.join("\n", lines); }
}
