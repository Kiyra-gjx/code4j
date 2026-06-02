package code4j.tools.builtin;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ShellSnippetPolicy {
    private static final Pattern CONTROL_OP = Pattern.compile(".*(&&|\\|\\||[|`<>]).*");
    private static final Set<String> SHELLS = Set.of("sh","bash","zsh","fish","cmd","cmd.exe","powershell","powershell.exe","pwsh","pwsh.exe");

    public boolean looksLikeShellSnippet(String command, List<String> args) {
        if (SHELLS.contains(execName(command))) return true;
        if (CONTROL_OP.matcher(command).matches()) return true;
        return args.stream().anyMatch(a -> CONTROL_OP.matcher(a).matches());
    }

    private static String execName(String cmd) { try { Path f = Path.of(cmd).getFileName(); return f == null ? cmd.toLowerCase(Locale.ROOT) : f.toString().toLowerCase(Locale.ROOT); } catch (RuntimeException e) { return cmd.toLowerCase(Locale.ROOT); } }
}
