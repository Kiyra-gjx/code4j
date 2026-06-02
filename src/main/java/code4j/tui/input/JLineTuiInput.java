package code4j.tui.input;

import org.jline.terminal.Terminal;
import org.jline.terminal.Attributes;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.Objects;

public final class JLineTuiInput implements TuiInput {
    private final NonBlockingReader reader;

    public JLineTuiInput(Terminal terminal) {
        Terminal t = Objects.requireNonNull(terminal, "terminal");
        t.enterRawMode();
        Attributes attrs = t.getAttributes();
        attrs.setLocalFlag(Attributes.LocalFlag.ICANON, false);
        attrs.setLocalFlag(Attributes.LocalFlag.ECHO, false);
        t.setAttributes(attrs);
        this.reader = t.reader();
    }

    @Override
    public TuiInputEvent readEvent() throws IOException {
        int v = reader.read();
        if (v < 0) return TuiInputEvent.eof();
        return switch (v) {
            case '\r', '\n' -> TuiInputEvent.submit();
            case '\b', 127 -> TuiInputEvent.backspace();
            case 27 -> readEscape();
            default -> TuiInputEvent.character((char) v);
        };
    }

    private TuiInputEvent readEscape() throws IOException {
        int n = reader.read();
        if (n < 0) return TuiInputEvent.eof();
        if (n != '[') return TuiInputEvent.character((char) n);
        int c = reader.read();
        if (c < 0) return TuiInputEvent.eof();
        return switch (c) {
            case 'A' -> TuiInputEvent.scrollUp();
            case 'B' -> TuiInputEvent.scrollDown();
            case 'C' -> TuiInputEvent.cursorRight();
            case 'D' -> TuiInputEvent.cursorLeft();
            case '5' -> reader.read() == '~' ? TuiInputEvent.pageUp() : TuiInputEvent.character('5');
            case '6' -> reader.read() == '~' ? TuiInputEvent.pageDown() : TuiInputEvent.character('6');
            default -> TuiInputEvent.character((char) c);
        };
    }
}
