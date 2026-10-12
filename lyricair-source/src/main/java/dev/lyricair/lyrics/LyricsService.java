package dev.lyricair.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lyricair.LyricAirClient;
import dev.lyricair.playback.TrackSnapshot;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class LyricsService implements AutoCloseable {
    public record Result(LyricTimeline timeline, String message) { }
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "lyricair-lyrics");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final Path localDirectory;
    private Future<?> pendingTask;
    private CompletableFuture<Result> pendingResult;
    private final Map<String, Result> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) { return size() > 64; }
    };

    public LyricsService(Path localDirectory) { this.localDirectory = localDirectory; }

    public synchronized CompletableFuture<Result> fetch(TrackSnapshot track) {
        if (pendingTask != null) pendingTask.cancel(true);
        if (pendingResult != null) pendingResult.cancel(false);
        CompletableFuture<Result> result = new CompletableFuture<>();
        pendingResult = result;
        pendingTask = executor.submit(() -> {
            try { result.complete(lookup(track)); }
            catch (RuntimeException e) { result.complete(new Result(LyricTimeline.EMPTY, "Lyrics lookup failed. Use Reconnect to retry.")); }
        });
        return result;
    }

    private Result lookup(TrackSnapshot track) {
        Path local = localDirectory.resolve(fileName(track.artist(), track.title()));
        try {
            if (Files.isRegularFile(local) && Files.size(local) <= 2_000_000) {
                LyricTimeline timeline = LyricTimeline.parse(Files.readString(local));
                return new Result(timeline, timeline.isEmpty() ? "Local LRC has no timed lines" : "Using local timed lyrics");
            }
        } catch (IOException e) { LyricAirClient.LOGGER.warn("Could not read local lyrics", e); }
        Result cached = cache.get(track.key());
        if (cached != null) return cached;
        String query = "track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist());
        if (!track.album().isBlank()) query += "&album_name=" + encode(track.album());
        long seconds = Math.round(track.durationMs() / 1000.0);
        if (seconds >= 1 && seconds <= 3_600) query += "&duration=" + seconds;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://lrclib.net/api/get?" + query))
                    .timeout(Duration.ofSeconds(12)).header("User-Agent", "LyricAir/0.2.0 (Minecraft client mod)")
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) return remember(track, new Result(LyricTimeline.EMPTY, "No timed lyrics found. You can add a local LRC."));
            if (response.statusCode() == 429) return new Result(LyricTimeline.EMPTY, "Lyrics service busy. Use Reconnect to retry later.");
            if (response.statusCode() != 200 || response.body().length() > 2_000_000)
                return new Result(LyricTimeline.EMPTY, "Lyrics service unavailable. Use Reconnect to retry.");
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (body.has("instrumental") && body.get("instrumental").getAsBoolean())
                return remember(track, new Result(LyricTimeline.EMPTY, "Instrumental track"));
            String synced = body.has("syncedLyrics") && !body.get("syncedLyrics").isJsonNull() ? body.get("syncedLyrics").getAsString() : "";
            LyricTimeline timeline = LyricTimeline.parse(synced);
            return remember(track, new Result(timeline, timeline.isEmpty() ? "This track has no timed lyrics" : "Timed lyrics ready"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(LyricTimeline.EMPTY, "Lyrics lookup cancelled");
        } catch (IOException | RuntimeException e) {
            LyricAirClient.LOGGER.debug("Lyrics lookup failed", e);
            return new Result(LyricTimeline.EMPTY, "Could not reach LRCLIB. Use Reconnect to retry.");
        }
    }

    private Result remember(TrackSnapshot track, Result result) { cache.put(track.key(), result); return result; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public static String fileName(String artist, String title) {
        String stem = (artist + " - " + title).replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_").strip();
        if (stem.length() > 180) stem = stem.substring(0, 180);
        return stem + ".lrc";
    }
    @Override public synchronized void close() {
        if (pendingTask != null) pendingTask.cancel(true);
        if (pendingResult != null) pendingResult.cancel(false);
        executor.shutdownNow();
        http.shutdownNow();
    }
}
