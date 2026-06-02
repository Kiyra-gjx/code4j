package code4j.tui.render;

import java.util.Objects;
import java.util.Optional;

public record StatusState(Optional<String> text) {
    public StatusState { text = Objects.requireNonNull(text, "text"); }
    public static StatusState empty() { return new StatusState(Optional.empty()); }
    public static StatusState thinking() { return of("Thinking..."); }
    public static StatusState of(String text) { return Objects.requireNonNull(text, "text").isBlank() ? empty() : new StatusState(Optional.of(text)); }
}
