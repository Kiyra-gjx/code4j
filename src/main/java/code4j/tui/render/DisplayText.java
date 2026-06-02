package code4j.tui.render;

final class DisplayText {
    private DisplayText() {}

    static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += isWide(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return w;
    }

    static boolean isWide(int cp) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || b == Character.UnicodeBlock.HIRAGANA || b == Character.UnicodeBlock.KATAKANA
                || b == Character.UnicodeBlock.HANGUL_SYLLABLES
                || b == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
