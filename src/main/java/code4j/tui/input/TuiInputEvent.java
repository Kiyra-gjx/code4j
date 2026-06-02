package code4j.tui.input;

import java.util.Objects;
import java.util.Optional;

public record TuiInputEvent(Kind kind, Optional<Character> character, Optional<String> text) {
    public TuiInputEvent { kind = Objects.requireNonNull(kind, "kind"); character = Objects.requireNonNull(character, "character"); text = Objects.requireNonNull(text, "text"); }

    public static TuiInputEvent character(char c) { return new TuiInputEvent(Kind.CHARACTER, Optional.of(c), Optional.empty()); }
    public static TuiInputEvent backspace() { return simple(Kind.BACKSPACE); }
    public static TuiInputEvent submit() { return simple(Kind.SUBMIT); }
    public static TuiInputEvent submitLine(String t) { return new TuiInputEvent(Kind.SUBMIT, Optional.empty(), Optional.of(t)); }
    public static TuiInputEvent eof() { return simple(Kind.EOF); }
    private static TuiInputEvent simple(Kind k) { return new TuiInputEvent(k, Optional.empty(), Optional.empty()); }

    public enum Kind { CHARACTER, BACKSPACE, SUBMIT, PAGE_UP, PAGE_DOWN, SCROLL_UP, SCROLL_DOWN, CURSOR_LEFT, CURSOR_RIGHT, EOF }
}
