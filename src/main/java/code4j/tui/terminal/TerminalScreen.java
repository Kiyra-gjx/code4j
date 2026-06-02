package code4j.tui.terminal;

import code4j.tui.render.RenderFrame;

public interface TerminalScreen extends AutoCloseable {
    TerminalSize size();
    void redraw(RenderFrame frame);
    @Override default void close() {}
}
