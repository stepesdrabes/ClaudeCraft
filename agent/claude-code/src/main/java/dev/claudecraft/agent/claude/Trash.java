package dev.claudecraft.agent.claude;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

final class Trash {
    private static final String OS = System.getProperty("os.name", "").toLowerCase();

    private Trash() {
    }

    static void move(Path path) throws IOException {
        if (!Files.exists(path)) return;
        if (OS.startsWith("windows")) {
            recycle(path);
        } else if (OS.contains("mac")) {
            Files.move(path, unique(Paths.get(System.getProperty("user.home"), ".Trash"), path.getFileName().toString()));
        } else {
            freedesktop(path);
        }
    }

    private static void freedesktop(Path path) throws IOException {
        String dataHome = System.getenv("XDG_DATA_HOME");
        Path trash = dataHome != null && !dataHome.isEmpty() ? Paths.get(dataHome, "Trash") : Paths.get(System.getProperty("user.home"), ".local", "share", "Trash");
        Path target = unique(trash.resolve("files"), path.getFileName().toString());
        Path info = trash.resolve("info").resolve(target.getFileName() + ".trashinfo");
        Files.createDirectories(info.getParent());
        String entry = "[Trash Info]\nPath=" + path.toAbsolutePath() + "\nDeletionDate="
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + "\n";
        Files.write(info, entry.getBytes(StandardCharsets.UTF_8));
        Files.move(path, target);
    }

    private static void recycle(Path path) throws IOException {
        String method = Files.isDirectory(path) ? "DeleteDirectory" : "DeleteFile";
        String script = "Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::" + method
            + "('" + path.toAbsolutePath().toString().replace("'", "''") + "', 'OnlyErrorDialogs', 'SendToRecycleBin')";
        Process process = new ProcessBuilder(Arrays.asList("powershell", "-NoProfile", "-Command", script)).redirectErrorStream(true).start();
        try {
            if (process.waitFor() != 0) throw new IOException("Could not move " + path.getFileName() + " to the Recycle Bin");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static Path unique(Path directory, String name) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(name);
        for (int i = 2; Files.exists(target); i++) target = directory.resolve(name + " " + i);
        return target;
    }
}
