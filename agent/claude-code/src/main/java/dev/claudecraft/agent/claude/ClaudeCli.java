package dev.claudecraft.agent.claude;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

final class ClaudeCli {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    private static final List<String> NAMES = WINDOWS ? Arrays.asList("claude.exe", "claude.cmd") : Arrays.asList("claude");
    private static final List<String> COMMON_DIRS = Arrays.asList(
        "~/.local/bin", "~/.claude/local", "/opt/homebrew/bin", "/usr/local/bin",
        "~/.npm-global/bin", "~/.bun/bin", "~/.volta/bin", "~/AppData/Roaming/npm");
    private static final String MARKER = "__CLAUDECRAFT_PATH__";

    private final String executable;
    private final Map<String, String> environment;

    private ClaudeCli(String executable, Map<String, String> environment) {
        this.executable = executable;
        this.environment = environment;
    }

    static ClaudeCli locate(String configuredPath) {
        Map<String, String> environment = new HashMap<>(System.getenv());
        String path = searchPath(environment.get("PATH"));
        environment.put("PATH", path);
        String executable = configuredPath != null && !configuredPath.trim().isEmpty()
            ? expandHome(configuredPath.trim())
            : find(path);
        if (executable == null || !new File(executable).canExecute()) {
            throw new IllegalStateException("Claude Code not found. Install it from claude.com/code or set claudePath in config/claudecraft.json");
        }
        return new ClaudeCli(executable, environment);
    }

    List<String> command(List<String> args) {
        List<String> command = new ArrayList<>();
        if (executable.endsWith(".cmd")) command.addAll(Arrays.asList("cmd.exe", "/c"));
        command.add(executable);
        command.addAll(args);
        return command;
    }

    Map<String, String> environment() {
        return environment;
    }

    private static String find(String path) {
        for (String dir : path.split(File.pathSeparator)) {
            for (String name : NAMES) {
                File candidate = new File(dir, name);
                if (candidate.isFile() && candidate.canExecute()) return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    private static String searchPath(String inherited) {
        Set<String> dirs = new LinkedHashSet<>();
        addAll(dirs, loginShellPath());
        addAll(dirs, inherited);
        for (String dir : COMMON_DIRS) dirs.add(expandHome(dir));
        return dirs.stream().filter(dir -> !dir.isEmpty()).collect(Collectors.joining(File.pathSeparator));
    }

    private static void addAll(Set<String> dirs, String path) {
        if (path != null) dirs.addAll(Arrays.asList(path.split(File.pathSeparator)));
    }

    private static String loginShellPath() {
        if (WINDOWS) return null;
        String shell = System.getenv().getOrDefault("SHELL", "/bin/zsh");
        try {
            Process process = new ProcessBuilder(shell, "-ilc", "printf '" + MARKER + "%s" + MARKER + "' \"$PATH\"")
                .redirectErrorStream(true)
                .start();
            process.getOutputStream().close();
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process));
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String text = output.get(1, TimeUnit.SECONDS);
            int start = text.indexOf(MARKER);
            int end = text.lastIndexOf(MARKER);
            return start >= 0 && end > start ? text.substring(start + MARKER.length(), end) : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | ExecutionException | TimeoutException e) {
            return null;
        }
    }

    private static String readAll(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "";
        }
    }

    private static String expandHome(String path) {
        return path.startsWith("~") ? System.getProperty("user.home") + path.substring(1) : path;
    }
}
