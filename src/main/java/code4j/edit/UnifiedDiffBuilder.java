package code4j.edit;

import code4j.permissions.model.PermissionResource.EditOperation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class UnifiedDiffBuilder {
    private static final int DEFAULT_MAX_PREVIEW_CHARS = 4096;
    private static final int CONTEXT_LINES = 3;
    private static final int LCS_CUTOFF = 4_000_000;
    private static final String TRUNCATED_MARKER = "[diff preview truncated]";

    private UnifiedDiffBuilder() {}

    public static EditReview build(Path path, EditOperation operation, String summary,
                                   Optional<String> beforeContent, String afterContent) {
        return build(path, operation, summary, beforeContent, afterContent, DEFAULT_MAX_PREVIEW_CHARS);
    }

    public static EditReview build(Path path, EditOperation operation, String summary,
                                   Optional<String> beforeContent, String afterContent,
                                   int maxPreviewChars) {
        Path actualPath = Objects.requireNonNull(path, "path");
        Objects.requireNonNull(operation, "operation");
        Optional<String> nb = Objects.requireNonNull(beforeContent, "beforeContent").map(UnifiedDiffBuilder::norm);
        String na = norm(Objects.requireNonNull(afterContent, "afterContent"));
        boolean exists = nb.isPresent();
        String preview = renderPreview(actualPath, nb, na);
        boolean truncated = preview.length() > maxPreviewChars;
        if (truncated) preview = truncate(preview, maxPreviewChars);
        long bc = nb.map(String::length).orElse(0);
        long ac = na.length();
        String fp = fingerprint(actualPath, operation, exists, nb, na);
        return new EditReview(actualPath, operation, summary, preview, bc, ac, exists, truncated, fp, Optional.of("sha256:" + fp));
    }

    private static String renderPreview(Path path, Optional<String> before, String after) {
        String pt = displayPath(path);
        String nb = before.orElse("");
        if (before.isPresent() && nb.equals(after)) return "--- a/" + pt + "\n+++ b/" + pt + "\n@@ no changes @@\n";
        List<String> bl = toLines(nb), al = toLines(after);
        if (!before.isPresent()) return renderCreate(pt, al);
        if (after.isEmpty()) return renderDelete(pt, bl);
        return renderMultiHunk(pt, bl, al);
    }

    private static String renderCreate(String pt, List<String> al) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- /dev/null\n+++ b/").append(pt).append('\n');
        sb.append(String.format("@@ -0,0 +1,%d @@\n", al.size()));
        for (String l : al) sb.append('+').append(l).append('\n');
        return sb.toString();
    }

    private static String renderDelete(String pt, List<String> bl) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(pt).append("\n+++ /dev/null\n");
        sb.append(String.format("@@ -1,%d +0,0 @@\n", bl.size()));
        for (String l : bl) sb.append('-').append(l).append('\n');
        return sb.toString();
    }

    private static String renderMultiHunk(String pt, List<String> bl, List<String> al) {
        List<DiffEntry> entries = buildEntries(bl, al);
        if (entries.stream().allMatch(e -> e.type == DiffType.KEEP)) {
            return "--- a/" + pt + "\n+++ b/" + pt + "\n@@ no changes @@\n";
        }
        List<Range> ranges = buildRanges(entries);
        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(pt).append("\n+++ b/").append(pt).append('\n');
        for (Range r : ranges) {
            List<DiffEntry> slice = entries.subList(r.start, r.endInclusive + 1);
            long os = slice.stream().filter(DiffEntry::contributesToOld).findFirst().map(e -> e.oldIndex + 1L).orElse(0L);
            long ns = slice.stream().filter(DiffEntry::contributesToNew).findFirst().map(e -> e.newIndex + 1L).orElse(0L);
            long oc = slice.stream().filter(DiffEntry::contributesToOld).count();
            long nc = slice.stream().filter(DiffEntry::contributesToNew).count();
            sb.append(String.format("@@ -%d,%d +%d,%d @@\n", os, oc, ns, nc));
            for (DiffEntry e : slice) sb.append(e.prefix()).append(e.text).append('\n');
        }
        return sb.toString();
    }

    private static List<DiffEntry> buildEntries(List<String> bl, List<String> al) {
        if ((long) bl.size() * (long) al.size() > LCS_CUTOFF) {
            return buildGreedyEntries(bl, al);
        }
        int[][] lcs = buildLcsTable(bl, al);
        List<DiffEntry> entries = new ArrayList<>();
        int i = 0, j = 0;
        while (i < bl.size() || j < al.size()) {
            if (i < bl.size() && j < al.size() && bl.get(i).equals(al.get(j))) {
                entries.add(new DiffEntry(DiffType.KEEP, bl.get(i), i, j)); i++; j++;
            } else if (j < al.size() && (i == bl.size() || lcs[i][j + 1] >= lcs[i + 1][j])) {
                entries.add(new DiffEntry(DiffType.INSERT, al.get(j), i, j)); j++;
            } else {
                entries.add(new DiffEntry(DiffType.DELETE, bl.get(i), i, j)); i++;
            }
        }
        return entries;
    }

    private static int[][] buildLcsTable(List<String> bl, List<String> al) {
        int[][] lcs = new int[bl.size() + 1][al.size() + 1];
        for (int i = bl.size() - 1; i >= 0; i--)
            for (int j = al.size() - 1; j >= 0; j--)
                if (bl.get(i).equals(al.get(j))) lcs[i][j] = lcs[i + 1][j + 1] + 1;
                else lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
        return lcs;
    }

    private static List<DiffEntry> buildGreedyEntries(List<String> bl, List<String> al) {
        List<DiffEntry> entries = new ArrayList<>(bl.size() + al.size());
        int i = 0, j = 0;
        while (i < bl.size() && j < al.size()) {
            if (bl.get(i).equals(al.get(j))) {
                entries.add(new DiffEntry(DiffType.KEEP, bl.get(i), i, j));
                i++; j++;
            } else {
                entries.add(new DiffEntry(DiffType.DELETE, bl.get(i), i, j));
                i++;
            }
        }
        while (i < bl.size()) {
            entries.add(new DiffEntry(DiffType.DELETE, bl.get(i), i, j));
            i++;
        }
        while (j < al.size()) {
            entries.add(new DiffEntry(DiffType.INSERT, al.get(j), i, j));
            j++;
        }
        return entries;
    }

    private static List<Range> buildRanges(List<DiffEntry> entries) {
        List<Range> ranges = new ArrayList<>();
        int idx = 0;
        while (idx < entries.size()) {
            while (idx < entries.size() && entries.get(idx).type == DiffType.KEEP) idx++;
            if (idx >= entries.size()) break;
            int start = Math.max(0, idx - CONTEXT_LINES);
            int lastChg = idx, end = idx, kt = 0, cur = idx;
            while (cur < entries.size()) {
                if (entries.get(cur).type == DiffType.KEEP) { kt++; if (kt > CONTEXT_LINES) break; }
                else { lastChg = cur; kt = 0; }
                end = cur; cur++;
            }
            end = Math.min(entries.size() - 1, Math.max(lastChg, end));
            ranges.add(new Range(start, end)); idx = end + 1;
        }
        return mergeRanges(ranges);
    }

    private static List<Range> mergeRanges(List<Range> rs) {
        if (rs.isEmpty()) return rs;
        List<Range> merged = new ArrayList<>();
        Range cur = rs.getFirst();
        for (int i = 1; i < rs.size(); i++) {
            Range next = rs.get(i);
            if (next.start <= cur.endInclusive + 1) cur = new Range(cur.start, Math.max(cur.endInclusive, next.endInclusive));
            else { merged.add(cur); cur = next; }
        }
        merged.add(cur);
        return merged;
    }

    private static List<String> toLines(String c) { return c.isEmpty() ? List.of() : c.lines().toList(); }

    private static String norm(String t) { return t.replace("\r\n", "\n").replace('\r', '\n'); }

    private static String displayPath(Path p) { return p.normalize().toString().replace('\\', '/'); }

    private static String truncate(String preview, int maxPreviewChars) {
        if (preview.length() <= maxPreviewChars) {
            return preview;
        }
        String marker = "\n" + TRUNCATED_MARKER;
        if (maxPreviewChars <= marker.length()) {
            return marker.substring(0, maxPreviewChars);
        }
        return preview.substring(0, maxPreviewChars - marker.length()) + marker;
    }

    private static String toHex(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String fingerprint(Path p, EditOperation op, boolean exists, Optional<String> before, String after) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update(displayPath(p).getBytes(StandardCharsets.UTF_8));
            d.update((byte) 0);
            d.update(op.name().getBytes(StandardCharsets.UTF_8));
            d.update((byte) 0);
            d.update((byte) (exists ? 1 : 0));
            d.update((byte) 0);
            d.update(before.orElse("").getBytes(StandardCharsets.UTF_8));
            d.update((byte) 0);
            d.update(after.getBytes(StandardCharsets.UTF_8));
            return toHex(d.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private enum DiffType { KEEP, DELETE, INSERT }
    private record DiffEntry(DiffType type, String text, int oldIndex, int newIndex) {
        boolean contributesToOld() { return type == DiffType.KEEP || type == DiffType.DELETE; }
        boolean contributesToNew() { return type == DiffType.KEEP || type == DiffType.INSERT; }
        char prefix() { return switch(type) { case KEEP -> ' '; case DELETE -> '-'; case INSERT -> '+'; }; }
    }
    private record Range(int start, int endInclusive) {}
}
