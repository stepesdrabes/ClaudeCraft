package dev.claudecraft.core.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClipboardImage {
    private static final String OS = System.getProperty("os.name", "").toLowerCase();
    private static final Pattern MAC_DATA = Pattern.compile("«data PNGf([0-9A-Fa-f]+)»");

    private ClipboardImage() {
    }

    public static byte[] read() {
        try {
            if (OS.contains("mac")) return mac();
            if (OS.startsWith("windows")) return windows();
            byte[] wayland = run(Arrays.asList("wl-paste", "--no-newline", "--type", "image/png"));
            return wayland != null ? wayland : run(Arrays.asList("xclip", "-selection", "clipboard", "-t", "image/png", "-o"));
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] mac() throws IOException {
        byte[] output = run(Arrays.asList("osascript", "-e", "the clipboard as «class PNGf»"));
        if (output == null) return null;
        Matcher matcher = MAC_DATA.matcher(new String(output, StandardCharsets.UTF_8));
        if (!matcher.find()) return null;
        String hex = matcher.group(1);
        byte[] png = new byte[hex.length() / 2];
        for (int i = 0; i < png.length; i++) png[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        return png;
    }

    private static byte[] windows() throws IOException {
        File file = File.createTempFile("claudecraft-clipboard", ".png");
        try {
            String script = "Add-Type -AssemblyName System.Windows.Forms; $image = [Windows.Forms.Clipboard]::GetImage(); "
                + "if ($image) { $image.Save('" + file.getAbsolutePath().replace("'", "''") + "') } else { exit 1 }";
            if (run(Arrays.asList("powershell", "-NoProfile", "-STA", "-Command", script)) == null) return null;
            return Files.readAllBytes(file.toPath());
        } finally {
            file.delete();
        }
    }

    private static byte[] run(List<String> command) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectError(new File(OS.startsWith("windows") ? "NUL" : "/dev/null")).start();
        } catch (IOException e) {
            return null;
        }
        process.getOutputStream().close();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] buffer = new byte[65536];
            for (int read; (read = in.read(buffer)) != -1; ) out.write(buffer, 0, read);
        }
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return out.size() > 0 ? out.toByteArray() : null;
    }
}
