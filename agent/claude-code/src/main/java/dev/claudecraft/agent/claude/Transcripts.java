package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSummary;
import dev.claudecraft.agent.ToolUse;
import dev.claudecraft.agent.json.Json;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Transcripts {
    private static final int MAX_DIRECTORY_NAME = 200;
    private static final int PEEK_BYTES = 64 * 1024;
    private static final int MAX_REPLAYED_MESSAGES = 400;
    private static final Set<String> CHAIN_TYPES = new HashSet<>(Arrays.asList("user", "assistant", "progress", "system", "attachment"));
    private static final Pattern COMMAND_NAME = Pattern.compile("<command-name>(.*?)</command-name>");
    private static final Pattern SKIPPED_PROMPT = Pattern.compile(
        "^(?:<local-command-stdout>|<local-command-stderr>|<session-start-hook>|<tick>|<goal>|<system-reminder>|"
            + "\\[Request interrupted by user[^\\]]*\\]|\\s*<ide_opened_file>[\\s\\S]*</ide_opened_file>\\s*$|"
            + "\\s*<ide_selection>[\\s\\S]*</ide_selection>\\s*$)");

    private final Path projects;

    Transcripts(Path projects) {
        this.projects = projects;
    }

    static Transcripts forCurrentUser() {
        String configDir = System.getenv("CLAUDE_CONFIG_DIR");
        Path home = configDir != null && !configDir.isEmpty() ? Paths.get(configDir) : Paths.get(System.getProperty("user.home"), ".claude");
        return new Transcripts(home.resolve("projects"));
    }

    List<SessionSummary> list(Path workspace, int limit) {
        List<SessionSummary> summaries = new ArrayList<>();
        for (Path file : newestFirst(directoryFor(workspace), "jsonl")) {
            if (summaries.size() >= limit) break;
            SessionSummary summary = summarize(file);
            if (summary != null) summaries.add(summary);
        }
        return summaries;
    }

    void replay(Path workspace, String sessionId, SessionListener listener) {
        Path file = directoryFor(workspace).resolve(sessionId + ".jsonl");
        List<Json> chain = conversationChain(readEntries(file));
        for (Json entry : chain.subList(Math.max(0, chain.size() - MAX_REPLAYED_MESSAGES), chain.size())) {
            if (!isVisible(entry)) continue;
            if ("assistant".equals(entry.get("type").asString())) replayAssistant(entry.get("message").get("content"), listener);
            else replayUser(entry.get("message").get("content"), listener);
        }
    }

    List<Path> recentWorkspaces(int limit) {
        Set<Path> workspaces = new LinkedHashSet<>();
        for (Path directory : newestFirst(projects, null)) {
            if (workspaces.size() >= limit) break;
            for (Path file : newestFirst(directory, "jsonl")) {
                Path cwd = workingDirectory(file);
                if (cwd != null && Files.isDirectory(cwd)) workspaces.add(cwd);
                if (cwd != null) break;
            }
        }
        return new ArrayList<>(workspaces);
    }

    static String text(Json content) {
        if (content.isString()) return content.asString();
        List<String> parts = new ArrayList<>();
        for (Json block : content.items()) {
            String type = block.get("type").asString("");
            if ("text".equals(type)) parts.add(block.get("text").asString(""));
            else if ("image".equals(type)) parts.add("[image]");
        }
        return String.join("\n", parts);
    }

    static String directoryName(String path) {
        String sanitized = path.replaceAll("[^a-zA-Z0-9]", "-");
        if (sanitized.length() <= MAX_DIRECTORY_NAME) return sanitized;
        return sanitized.substring(0, MAX_DIRECTORY_NAME) + "-" + javascriptHash(path);
    }

    private static String javascriptHash(String text) {
        int hash = 0;
        for (int i = 0; i < text.length(); i++) hash = (hash << 5) - hash + text.charAt(i);
        return Long.toString(Math.abs((long) hash), 36);
    }

    private Path directoryFor(Path workspace) {
        return projects.resolve(directoryName(canonical(workspace)));
    }

    private static String canonical(Path workspace) {
        Path path;
        try {
            path = workspace.toRealPath();
        } catch (IOException e) {
            path = workspace.toAbsolutePath().normalize();
        }
        return Normalizer.normalize(path.toString(), Normalizer.Form.NFC);
    }

    private static SessionSummary summarize(Path file) {
        String head = peek(file, false);
        String tail = peek(file, true);
        if (head.isEmpty() || firstLine(head).contains("\"isSidechain\":true")) return null;
        String title = firstNonEmpty(
            lastField(tail, "customTitle"), lastField(head, "customTitle"),
            lastField(tail, "aiTitle"), lastField(head, "aiTitle"),
            lastField(tail, "lastPrompt"), lastField(tail, "summary"),
            firstPrompt(head));
        if (title == null) return null;
        String id = file.getFileName().toString().replaceFirst("\\.jsonl$", "");
        return new SessionSummary(id, title.replace('\n', ' ').trim(), lastModified(file));
    }

    private static String lastField(String text, String key) {
        List<String> lines = lines(text);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (!line.contains("\"" + key + "\"")) continue;
            String value = tryParse(line).get(key).asString();
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }

    private static String firstPrompt(String head) {
        String commandFallback = null;
        for (String line : lines(head)) {
            if (!line.contains("\"type\":\"user\"") || line.contains("\"tool_result\"")
                || line.contains("\"isMeta\":true") || line.contains("\"isCompactSummary\":true")) continue;
            String text = text(tryParse(line).get("message").get("content")).replace('\n', ' ').trim();
            if (text.isEmpty()) continue;
            Matcher command = COMMAND_NAME.matcher(text);
            if (command.find()) {
                if (commandFallback == null) commandFallback = command.group(1);
            } else if (!SKIPPED_PROMPT.matcher(text).find()) {
                return text;
            }
        }
        return commandFallback;
    }

    private static List<Json> readEntries(Path file) {
        List<Json> entries = new ArrayList<>();
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.map(Transcripts::tryParse)
                .filter(entry -> CHAIN_TYPES.contains(entry.get("type").asString("")) && entry.get("uuid").isString())
                .forEach(entries::add);
        } catch (IOException | UncheckedIOException e) {
            return Collections.emptyList();
        }
        return entries;
    }

    private static List<Json> conversationChain(List<Json> entries) {
        Map<String, Json> byUuid = new HashMap<>();
        Map<String, Integer> position = new HashMap<>();
        Set<String> parents = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            Json entry = entries.get(i);
            byUuid.put(uuid(entry), entry);
            position.put(uuid(entry), i);
            String parent = entry.get("parentUuid").asString();
            if (parent != null) parents.add(parent);
        }
        Json leaf = null;
        for (Json entry : entries) {
            if (parents.contains(uuid(entry))) continue;
            Json message = nearestMessage(entry, byUuid);
            if (message != null && isMainChain(message) && (leaf == null || position.get(uuid(message)) > position.get(uuid(leaf)))) {
                leaf = message;
            }
        }
        List<Json> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Json cursor = leaf; cursor != null && seen.add(uuid(cursor)); cursor = parentOf(cursor, byUuid)) chain.add(cursor);
        Collections.reverse(chain);
        return chain;
    }

    private static Json nearestMessage(Json entry, Map<String, Json> byUuid) {
        Set<String> seen = new HashSet<>();
        for (Json cursor = entry; cursor != null && seen.add(uuid(cursor)); cursor = parentOf(cursor, byUuid)) {
            if (isMessage(cursor)) return cursor;
        }
        return null;
    }

    private static Json parentOf(Json entry, Map<String, Json> byUuid) {
        String parent = entry.get("parentUuid").asString();
        return parent == null ? null : byUuid.get(parent);
    }

    private static boolean isMessage(Json entry) {
        String type = entry.get("type").asString("");
        return "user".equals(type) || "assistant".equals(type);
    }

    private static boolean isMainChain(Json entry) {
        return !entry.get("isSidechain").asBoolean(false) && !entry.get("isMeta").asBoolean(false) && !entry.has("teamName");
    }

    private static String uuid(Json entry) {
        return entry.get("uuid").asString();
    }

    private static boolean isVisible(Json entry) {
        return isMessage(entry) && isMainChain(entry) && !entry.get("isCompactSummary").asBoolean(false);
    }

    private static void replayAssistant(Json content, SessionListener listener) {
        for (Json block : content.items()) {
            String type = block.get("type").asString("");
            if ("text".equals(type)) listener.onText(block.get("text").asString(""));
            else if ("tool_use".equals(type)) listener.onToolUse(new ToolUse(block.get("id").asString(""), block.get("name").asString(""), block.get("input")));
        }
    }

    private static void replayUser(Json content, SessionListener listener) {
        if (content.isString()) {
            replayPrompt(content.asString(), listener);
            return;
        }
        for (Json block : content.items()) {
            String type = block.get("type").asString("");
            if ("text".equals(type)) replayPrompt(block.get("text").asString(""), listener);
            else if ("tool_result".equals(type)) {
                listener.onToolResult(block.get("tool_use_id").asString(""), text(block.get("content")), block.get("is_error").asBoolean(false));
            }
        }
    }

    private static void replayPrompt(String text, SessionListener listener) {
        Matcher command = COMMAND_NAME.matcher(text);
        if (command.find()) listener.onUserMessage(command.group(1));
        else if (!text.trim().isEmpty() && !SKIPPED_PROMPT.matcher(text).find()) listener.onUserMessage(text);
    }

    private static Path workingDirectory(Path file) {
        String cwd = lastField(peek(file, false), "cwd");
        return cwd == null ? null : Paths.get(cwd);
    }

    private static List<Path> newestFirst(Path directory, String extension) {
        if (!Files.isDirectory(directory)) return Collections.emptyList();
        try (Stream<Path> children = Files.list(directory)) {
            return children
                .filter(path -> extension == null ? Files.isDirectory(path) : path.toString().endsWith("." + extension))
                .sorted((a, b) -> Long.compare(lastModified(b), lastModified(a)))
                .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static String peek(Path file, boolean fromEnd) {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            long length = in.length();
            int size = (int) Math.min(length, PEEK_BYTES);
            byte[] bytes = new byte[size];
            in.seek(fromEnd ? length - size : 0);
            in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static long lastModified(Path path) {
        return path.toFile().lastModified();
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    private static String firstLine(String text) {
        int end = text.indexOf('\n');
        return end < 0 ? text : text.substring(0, end);
    }

    private static Json tryParse(String line) {
        try {
            return Json.parse(line);
        } catch (IllegalArgumentException e) {
            return Json.NULL;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return null;
    }
}
