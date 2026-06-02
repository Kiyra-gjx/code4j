package code4j.tui.render;

import code4j.tui.terminal.TerminalSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TuiRenderer {
    private static final int CHROME_ROWS = 4;

    public RenderFrame render(RenderState state, TerminalSize size) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(size, "size");
        int w = size.columns(), h = size.rows();
        List<String> rendered = state.transcript().stream().map(TranscriptBlock::renderText)
                .flatMap(t -> wrap(t, w).stream()).toList();
        if (rendered.isEmpty()) rendered = List.of(" ".repeat(w));
        int transcriptRows = Math.max(1, h - CHROME_ROWS);
        int end = Math.max(0, rendered.size() - state.scrollOffset());
        int start = Math.max(0, end - transcriptRows);
        ArrayList<String> lines = new ArrayList<>();
        lines.add(fit("Code4j", w));
        List<String> view = new ArrayList<>(rendered.subList(start, end));
        while (view.size() < transcriptRows) view.add(0, "");
        for (String line : view) lines.add(fit(line, w));
        lines.add(fit("-".repeat(w), w));
        lines.add(fit(state.status().text().orElse("Ready"), w));
        String prompt = switch (state.input().mode()) {
            case BUSY -> "busy> "; case AWAITING_ASK_USER -> "answer> ";
            case PENDING_PERMISSION -> "permission> "; case PERMISSION_FEEDBACK -> "feedback> ";
            default -> "> ";
        };
        String inputLine = prompt + state.input().text();
        lines.add(fit(inputLine, w));
        return new RenderFrame(lines, h, prompt.length() + state.input().cursor() + 1);
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.replace("\r", "").split("\n", -1)) {
            StringBuilder sb = new StringBuilder(); int lw = 0;
            for (int i = 0; i < raw.length(); ) {
                int cp = raw.codePointAt(i);
                int cw = DisplayText.isWide(cp) ? 2 : 1;
                if (lw > 0 && lw + cw > width) { lines.add(sb.toString()); sb.setLength(0); lw = 0; }
                sb.appendCodePoint(cp); lw += cw;
                i += Character.charCount(cp);
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    private static String fit(String text, int width) {
        StringBuilder sb = new StringBuilder(); int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i); int cw = DisplayText.isWide(cp) ? 2 : 1;
            if (w + cw > width) break;
            sb.appendCodePoint(cp); w += cw;
            i += Character.charCount(cp);
        }
        return sb + " ".repeat(width - w);
    }
}
