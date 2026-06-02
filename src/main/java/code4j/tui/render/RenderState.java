package code4j.tui.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RenderState(List<TranscriptBlock> transcript, StatusState status, InputState input, int scrollOffset,
                          Optional<String> contextBadge) {
    public RenderState {
        transcript = List.copyOf(Objects.requireNonNull(transcript, "transcript"));
        status = Objects.requireNonNull(status, "status");
        input = Objects.requireNonNull(input, "input");
        contextBadge = Objects.requireNonNull(contextBadge, "contextBadge");
        if (scrollOffset < 0) throw new IllegalArgumentException("scrollOffset must be non-negative");
    }

    public static RenderState empty() { return new RenderState(List.of(), StatusState.empty(), InputState.empty(), 0, Optional.empty()); }

    public RenderState withTranscript(List<TranscriptBlock> t) { return new RenderState(t, status, input, scrollOffset, contextBadge); }
    public RenderState withStatus(StatusState s) { return new RenderState(transcript, s, input, scrollOffset, contextBadge); }
    public RenderState withInput(InputState i) { return new RenderState(transcript, status, i, scrollOffset, contextBadge); }
    public RenderState withScrollOffset(int o) { return new RenderState(transcript, status, input, o, contextBadge); }
    public RenderState withContextBadge(String b) { return new RenderState(transcript, status, input, scrollOffset, b == null || b.isBlank() ? Optional.empty() : Optional.of(b)); }

    public RenderState appendTranscript(TranscriptBlock block) {
        ArrayList<TranscriptBlock> next = new ArrayList<>(transcript);
        next.add(block);
        return withTranscript(next);
    }
}
