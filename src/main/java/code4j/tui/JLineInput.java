package code4j.tui;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.io.IOException;

public final class JLineInput implements LineInput {
    private final LineReader reader;

    public JLineInput(LineReader reader) { this.reader = reader; }

    @Override
    public String readLine() throws IOException {
        try { return reader.readLine(); }
        catch (EndOfFileException e) { return null; }
        catch (UserInterruptException e) { throw new IOException("Interrupted", e); }
    }
}
