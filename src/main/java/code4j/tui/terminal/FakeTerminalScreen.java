package code4j.tui.terminal;

import code4j.tui.render.RenderFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FakeTerminalScreen implements TerminalScreen {
    private final TerminalSize size;
    private final ArrayList<RenderFrame> frames = new ArrayList<>();

    public FakeTerminalScreen(TerminalSize size) { this.size = Objects.requireNonNull(size, "size"); }

    @Override public TerminalSize size() { return size; }
    @Override public void redraw(RenderFrame frame) { frames.add(Objects.requireNonNull(frame, "frame")); }
    public String latestText() { return frames.isEmpty() ? "" : frames.getLast().text(); }
    public List<RenderFrame> frames() { return List.copyOf(frames); }
}
