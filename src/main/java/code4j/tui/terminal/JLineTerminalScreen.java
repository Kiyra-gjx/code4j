package code4j.tui.terminal;

import code4j.tui.render.RenderFrame;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.util.Objects;

public final class JLineTerminalScreen implements TerminalScreen {
    private static final String ALT_SCREEN_ON = "[?1049h";
    private static final String ALT_SCREEN_OFF = "[?1049l";
    private static final String CLEAR = "[2J";
    private static final String HOME = "[H";
    private static final String SHOW_CURSOR = "[?25h";

    private final Terminal terminal;
    private final PrintWriter writer;
    private boolean closed;

    public JLineTerminalScreen(Terminal terminal) {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.writer = terminal.writer();
        writer.print(ALT_SCREEN_ON); writer.print(CLEAR); writer.print(HOME); writer.print(SHOW_CURSOR); writer.flush();
    }

    @Override public TerminalSize size() { return new TerminalSize(Math.max(1, terminal.getWidth()), Math.max(1, terminal.getHeight())); }

    @Override
    public void redraw(RenderFrame frame) {
        Objects.requireNonNull(frame, "frame");
        writer.print(SHOW_CURSOR); writer.print(HOME); writer.print(CLEAR); writer.print(HOME);
        writer.print(String.join("\r\n", frame.lines()));
        if (frame.cursorRow() > 0 && frame.cursorColumn() > 0) writer.print("[" + frame.cursorRow() + ";" + frame.cursorColumn() + "H");
        writer.flush();
    }

    @Override
    public void close() {
        if (closed) return; closed = true;
        writer.print(SHOW_CURSOR); writer.print(ALT_SCREEN_OFF); writer.flush();
    }
}
