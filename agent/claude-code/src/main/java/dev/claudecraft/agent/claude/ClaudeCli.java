package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.Installation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class ClaudeCli {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    private static final List<String> NAMES = WINDOWS ? Arrays.asList("claude.exe", "claude.cmd") : Arrays.asList("claude");
    private static final List<String> COMMON_DIRS = Arrays.asList(
        "~/.local/bin", "~/.claude/local", "/opt/homebrew/bin", "/usr/local/bin",
        "~/.npm-global/bin", "~/.bun/bin", "~/.volta/bin", "~/AppData/Roaming/npm");
    private static final String MARKER = "__CLAUDECRAFT_PATH__";
    private static final Pattern VERSION = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
    private static volatile String searchPath;

    private final String executable;
    private final Map<String, String> environment;
    private volatile String version;

    private ClaudeCli(String executable, Map<String, String> environment) {
        this.executable = executable;
        this.environment = environment;
    }

    static ClaudeCli locate(String configuredPath) {
        Map<String, String> environment = environmentWithPath();
        String path = environment.get("PATH");
        String executable = configuredPath != null && !configuredPath.trim().isEmpty()
            ? expandHome(configuredPath.trim())
            : candidates(path).stream().findFirst().orElse(null);
        if (executable == null || !new File(executable).canExecute()) {
            throw new IllegalStateException("Claude Code not found. Install it from claude.com/code or pick it in the model menu");
        }
        return new ClaudeCli(executable, environment);
    }

    static List<Installation> installations() {
        Map<String, String> byRealPath = new LinkedHashMap<>();
        for (String candidate : candidates(searchPath())) {
            try {
                byRealPath.putIfAbsent(new File(candidate).getCanonicalPath(), candidate);
            } catch (IOException ignored) {
            }
        }
        List<CompletableFuture<Installation>> probes = new ArrayList<>();
        for (String path : byRealPath.values()) {
            probes.add(CompletableFuture.supplyAsync(() -> new Installation(path, versionOf(path, environmentWithPath()))));
        }
        return probes.stream().map(CompletableFuture::join).filter(i -> i.version() != null).collect(Collectors.toList());
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

    String executable() {
        return executable;
    }

    String version() {
        if (version == null) version = versionOf(executable, environment);
        return version;
    }

    String run(List<String> args, int timeoutSeconds) throws IOException {
        return run(args, null, timeoutSeconds);
    }

    String run(List<String> args, Path directory, int timeoutSeconds) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command(args)).redirectErrorStream(true);
        if (directory != null) builder.directory(directory.toFile());
        builder.environment().clear();
        builder.environment().putAll(environment);
        Process process = builder.start();
        process.getOutputStream().close();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process));
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Claude Code did not answer in time");
            }
            String text = output.get(2, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new IOException(text.trim().isEmpty() ? "exit code " + process.exitValue() : text.trim());
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static String versionOf(String executable, Map<String, String> environment) {
        try {
            String output = new ClaudeCli(executable, environment).run(Arrays.asList("--version"), 10);
            Matcher matcher = VERSION.matcher(output);
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static Map<String, String> environmentWithPath() {
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("PATH", searchPath());
        return environment;
    }

    private static List<String> candidates(String path) {
        List<String> found = new ArrayList<>();
        for (String dir : path.split(File.pathSeparator)) {
            for (String name : NAMES) {
                File candidate = new File(dir, name);
                if (candidate.isFile() && candidate.canExecute()) found.add(candidate.getAbsolutePath());
            }
        }
        return found;
    }

    private static String searchPath() {
        if (searchPath == null) {
            Set<String> dirs = new LinkedHashSet<>();
            addAll(dirs, loginShellPath());
            addAll(dirs, System.getenv("PATH"));
            for (String dir : COMMON_DIRS) dirs.add(expandHome(dir));
            searchPath = dirs.stream().filter(dir -> !dir.isEmpty()).collect(Collectors.joining(File.pathSeparator));
        }
        return searchPath;
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

    static String expandHome(String path) {
        return path.startsWith("~") ? System.getProperty("user.home") + path.substring(1) : path;
    }
}
