package code4j.tools.builtin;

import code4j.permissions.model.CommandClassification;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandClassifier {
    private static final Set<String> READONLY = Set.of("pwd","ls","dir","cat","type","git","mvn","java","node","npm");
    private static final Set<String> DEV_WORDS = Set.of("test","build","compile","package","verify","lint","format","check");
    private static final Set<String> DANGEROUS = Set.of("rm","del","erase","rmdir","rd","mv","move","cp","copy","chmod","chown","sudo","su","shutdown","reboot","mkfs","diskpart","reg","sc");
    private static final Set<String> DANGER_WORDS = Set.of("delete","remove","clean","prune","reset","checkout","install","publish","force");

    private final ShellSnippetPolicy shellPolicy;

    public CommandClassifier() { this(new ShellSnippetPolicy()); }
    public CommandClassifier(ShellSnippetPolicy p) { this.shellPolicy = p; }

    public CommandClassificationResult classify(String command, List<String> args) {
        if (shellPolicy.looksLikeShellSnippet(command, args)) return new CommandClassificationResult(CommandClassification.SENSITIVE, true, "shell snippet detected");
        String exe = execName(command);
        List<String> na = args.stream().map(a -> a.toLowerCase(Locale.ROOT).trim()).toList();
        if (DANGEROUS.contains(exe) || na.stream().anyMatch(a -> DANGER_WORDS.stream().anyMatch(a::contains)))
            return new CommandClassificationResult(CommandClassification.DANGEROUS, false, "dangerous command");
        if (isReadonly(exe, na)) return new CommandClassificationResult(CommandClassification.READONLY, false, "readonly");
        if (na.stream().anyMatch(DEV_WORDS::contains)) return new CommandClassificationResult(CommandClassification.DEVELOPMENT, false, "development");
        return new CommandClassificationResult(CommandClassification.UNKNOWN, false, "unknown");
    }

    private static boolean isReadonly(String exe, List<String> args) {
        if (!READONLY.contains(exe)) return false;
        if (exe.equals("pwd")) return true;
        if (exe.equals("ls") || exe.equals("dir")) return args.isEmpty();
        if (exe.equals("git")) return args.equals(List.of("status")) || args.equals(List.of("diff")) || args.equals(List.of("log"));
        if (exe.equals("mvn")) return args.equals(List.of("-version")) || args.equals(List.of("--version"));
        if (exe.equals("java") || exe.equals("node") || exe.equals("npm")) return args.equals(List.of("-version")) || args.equals(List.of("--version")) || args.equals(List.of("version"));
        return false;
    }

    private static String execName(String cmd) { try { Path f = Path.of(cmd).getFileName(); String n = f == null ? cmd : f.toString(); n = n.toLowerCase(Locale.ROOT).trim(); if (n.endsWith(".exe")||n.endsWith(".cmd")||n.endsWith(".bat")) n = n.substring(0, n.length()-4); return n; } catch (RuntimeException e) { return cmd.toLowerCase(Locale.ROOT).trim(); } }
}
