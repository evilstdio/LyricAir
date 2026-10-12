package dev.lyricair.playback;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lyricair.LyricAirClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Base64;

public final class WindowsSpotifyBridge implements AutoCloseable {
    public record Artwork(String trackKey, byte[] png) {}
    private volatile TrackSnapshot snapshot;
    private volatile Artwork artwork;
    private volatile String status = "Starting Spotify connection...";
    private volatile boolean running;
    private volatile Process process;
    private volatile Thread worker;

    public TrackSnapshot snapshot() { return snapshot; }
    public Artwork artwork() { return artwork; }
    public String status() { return status; }

    public synchronized void start(Path directory) {
        close();
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            status = "Spotify desktop sync needs Windows 10/11. Demo still works.";
            return;
        }
        try {
            Files.createDirectories(directory);
            Path script = directory.resolve("spotify-media.ps1");
            try (InputStream input = WindowsSpotifyBridge.class.getResourceAsStream("/bridge/spotify-media.ps1")) {
                if (input == null) throw new IOException("Missing embedded Spotify bridge");
                Files.copy(input, script, StandardCopyOption.REPLACE_EXISTING);
            }
            running = true;
            status = "Connecting to Spotify desktop...";
            worker = new Thread(() -> readLoop(script), "lyricair-spotify");
            worker.setDaemon(true);
            worker.start();
        } catch (IOException e) {
            status = "Could not start Spotify bridge. See Minecraft log.";
            LyricAirClient.LOGGER.warn("Could not extract Spotify bridge", e);
        }
    }

    private void readLoop(Path script) {
        Thread owner = Thread.currentThread();
        while (running && !owner.isInterrupted()) {
            Process child = null;
            try {
                String windows = System.getenv("SystemRoot");
                Path powershell = Path.of(windows == null ? "C:\\Windows" : windows,
                        "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
                child = new ProcessBuilder(powershell.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
                        "-ExecutionPolicy", "Bypass", "-File", script.toAbsolutePath().toString(),
                        "-ParentPid", Long.toString(ProcessHandle.current().pid()))
                        .redirectErrorStream(true).start();
                synchronized (this) {
                    if (worker != owner || !running) { child.destroyForcibly(); return; }
                    process = child;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!owner.isInterrupted() && (line = reader.readLine()) != null) {
                        synchronized (this) {
                            if (worker == owner && running && !owner.isInterrupted()) accept(line);
                        }
                    }
                }
                if (!owner.isInterrupted()) {
                    snapshot = null;
                    status = "Spotify bridge stopped. Reconnecting...";
                }
            } catch (IOException e) {
                if (!owner.isInterrupted()) {
                    snapshot = null;
                    status = "Spotify connection failed. Retrying...";
                    LyricAirClient.LOGGER.warn("Spotify bridge process failed", e);
                }
            } finally {
                if (child != null) child.destroyForcibly();
            }
            try { Thread.sleep(5_000); }
            catch (InterruptedException e) { owner.interrupt(); }
        }
    }

    private void accept(String line) {
        if (!line.startsWith("{") || line.length() > 1_000_000) return;
        try {
            JsonObject message = JsonParser.parseString(line).getAsJsonObject();
            if ("playback".equals(message.get("type").getAsString())) {
                long latencyMs = Math.clamp(System.currentTimeMillis() - message.get("sampledAtMs").getAsLong(), 0, 2_000);
                double rate = message.get("playbackRate").getAsDouble();
                if (!Double.isFinite(rate) || rate <= 0 || rate > 4) rate = 1;
                snapshot = new TrackSnapshot(string(message, "title"), string(message, "artist"), string(message, "album"),
                        Math.max(0, message.get("durationMs").getAsLong()), Math.max(0, message.get("positionMs").getAsLong()),
                        message.get("playing").getAsBoolean(), rate, System.nanoTime() - latencyMs * 1_000_000L);
                if (artwork != null && !artwork.trackKey().equals(snapshot.key())) artwork = null;
                String cover = string(message, "coverPng");
                if (!cover.isEmpty() && cover.length() < 500_000) {
                    byte[] png = Base64.getDecoder().decode(cover);
                    artwork = new Artwork(snapshot.key(), png);
                }
                status = snapshot.playing() ? "Connected to Spotify" : "Spotify paused";
            } else {
                snapshot = null;
                artwork = null;
                status = string(message, "message");
            }
        } catch (RuntimeException e) { LyricAirClient.LOGGER.debug("Ignoring malformed media bridge sample", e); }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    @Override public synchronized void close() {
        running = false;
        if (worker != null) worker.interrupt();
        if (process != null) process.destroyForcibly();
        worker = null;
        process = null;
        snapshot = null;
        artwork = null;
    }
}

