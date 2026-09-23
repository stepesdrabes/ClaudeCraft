package dev.claudecraft.agent.mcp;

import dev.claudecraft.agent.json.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class McpStdioBridge {
    private McpStdioBridge() {
    }

    public static void main(String[] args) throws IOException {
        String url = args.length > 0 ? args[0] : "http://127.0.0.1:25595" + McpHttpServer.PATH;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(System.out, true, "UTF-8");
        for (String line; (line = in.readLine()) != null; ) {
            if (line.trim().isEmpty()) continue;
            String response = forward(url, line);
            if (response != null) out.println(response);
        }
    }

    private static String forward(String url, String message) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            try (OutputStream body = connection.getOutputStream()) {
                body.write(message.getBytes(StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() != 200) return null;
            try (InputStream body = connection.getInputStream()) {
                return Streams.readUtf8(body).trim();
            }
        } catch (IOException e) {
            Json request = Json.parse(message);
            if (!request.has("id")) return null;
            return McpServer.error(request.get("id"), -32000, "Minecraft is not running or ClaudeCraft's MCP server is off").toString();
        }
    }

}
