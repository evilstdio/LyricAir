package dev.lyricair;

import dev.lyricair.config.LyricAirConfig;
import dev.lyricair.lyrics.CueCursor;
import dev.lyricair.lyrics.LyricTimeline;
import dev.lyricair.lyrics.LyricsService;
import dev.lyricair.playback.TrackSnapshot;
import dev.lyricair.playback.WindowsSpotifyBridge;
import dev.lyricair.render.FloatingLyric;
import dev.lyricair.render.FollowAnchor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class LyricController implements AutoCloseable {
    private static final long DEMO_LENGTH_MS = 28_000;
    // Original demo text; no music or copyrighted lyrics are bundled.
    private static final LyricTimeline DEMO = LyricTimeline.parse("""
            [00:00.00] Let the words take flight
            [00:04.00] A little light above the trees
            [00:08.00] Turn the night into a skyline
            [00:12.00] Every line becomes a constellation
            [00:16.00] We leave a trail of floating echoes
            [00:20.00] Watch them drift into the blue
            [00:24.00] The whole world sings with you
            """);
    private final Minecraft client;
    private final LyricAirConfig config;
    private final Path dataDirectory;
    private final WindowsSpotifyBridge bridge = new WindowsSpotifyBridge();
    private final LyricsService lyrics;
    private final CueCursor cursor = new CueCursor();
    private final List<FloatingLyric> bubbles = new ArrayList<>();
    private LyricTimeline timeline = LyricTimeline.EMPTY;
    private TrackSnapshot activeTrack;
    private String trackKey = "";
    private String lyricStatus = "Waiting for a song";
    private long requestVersion;
    private long previousNanos = System.nanoTime();
    private long demoStarted;
    private double visualTime;
    private boolean advancing;
    private ClientLevel previousWorld;
    private int sequence;

    public LyricController(Minecraft client, LyricAirConfig config, Path dataDirectory) {
        this.client = client;
        this.config = config;
        this.dataDirectory = dataDirectory;
        this.lyrics = new LyricsService(dataDirectory.resolve("lyrics"));
        bridge.start(dataDirectory);
    }

    public List<FloatingLyric> bubbles() { return bubbles; }
    public double visualTime() { return visualTime; }
    public boolean advancing() { return advancing; }
    public boolean demo() { return demoStarted != 0; }
    public TrackSnapshot track() { return activeTrack; }
    public LyricTimeline timeline() { return timeline; }
    public WindowsSpotifyBridge.Artwork artwork() {
        var artwork = bridge.artwork();
        return !demo() && activeTrack != null && artwork != null && artwork.trackKey().equals(activeTrack.key()) ? artwork : null;
    }
    public long position() { return activeTrack == null ? 0 : activeTrack.positionAt(System.nanoTime()); }
    public long lyricPosition() { return Math.max(0, position() + config.timingOffsetMs); }
    public String status() {
        if (demo()) return "Demo: original sample lyrics";
        TrackSnapshot sample = bridge.snapshot();
        if (sample == null) return bridge.status();
        if (sample.stale(System.nanoTime())) return "Spotify stopped updating. Use Reconnect.";
        return sample.playing() ? lyricStatus : "Spotify paused | " + lyricStatus;
    }

    public void toggleDemo() {
        demoStarted = demo() ? 0 : System.nanoTime();
        if (demo()) config.enabled = true;
        resetTrack();
    }

    public void reconnect() {
        bridge.start(dataDirectory);
        resetTrack();
    }

    private void resetTrack() {
        requestVersion++;
        trackKey = "";
        activeTrack = null;
        timeline = LyricTimeline.EMPTY;
        cursor.reset();
        bubbles.clear();
    }

    public void clearVisuals() { bubbles.clear(); cursor.reset(); }

    public void tick() {
        long now = System.nanoTime();
        double delta = Math.clamp((now - previousNanos) / 1_000_000_000.0, 0, 0.2);
        previousNanos = now;
        if (client.level != previousWorld) {
            clearVisuals();
            previousWorld = client.level;
        }
        if (!config.enabled || client.level == null || client.player == null) {
            bubbles.clear();
            cursor.reset();
            advancing = false;
            return;
        }
        TrackSnapshot track = demo() ? new TrackSnapshot("Floating echoes", "LyricAir demo", "", DEMO_LENGTH_MS,
                ((now - demoStarted) / 1_000_000L) % DEMO_LENGTH_MS, true, 1, now) : bridge.snapshot();
        if (track == null || track.stale(now)) {
            if (activeTrack != null) resetTrack();
            advancing = false;
            return;
        }
        activeTrack = track;
        advancing = track.playing();
        if (advancing) visualTime += delta;
        bubbles.removeIf(bubble -> bubble.expires <= visualTime || (!config.followPlayer && bubble.origin.distanceToSqr(client.player.position()) > 48 * 48));
        if (!track.key().equals(trackKey)) {
            trackKey = track.key();
            timeline = LyricTimeline.EMPTY;
            cursor.reset();
            bubbles.clear();
            long version = ++requestVersion;
            if (demo()) timeline = DEMO;
            else {
                lyricStatus = "Looking up timed lyrics...";
                lyrics.fetch(track).thenAccept(result -> client.execute(() -> {
                    if (version != requestVersion) return;
                    timeline = result.timeline();
                    lyricStatus = result.message();
                    cursor.reset();
                }));
            }
        }
        long position = Math.max(0, track.positionAt(now) + config.timingOffsetMs);
        int cue = cursor.update(timeline, position);
        if (cursor.sought()) bubbles.clear();
        if (cue >= 0) {
            String text = timeline.lines().get(cue).text();
            for (FloatingLyric bubble : bubbles) bubble.expires = Math.min(bubble.expires, visualTime + 1.3);
            if (!text.isBlank()) {
                double remaining = timeline.remainingMs(cue, position, track.durationMs()) / 1000.0;
                if (config.displayMode.world()) spawn(text, Math.clamp(remaining, 0.5, 8.0) + 0.9);
            }
        }
    }

    private void spawn(String text, double lifetime) {
        var camera = client.gameRenderer.getMainCamera();
        Vec3 forward = FollowAnchor.forward(camera.getYRot());
        Vec3 right = FollowAnchor.right(camera.getYRot());
        double side = config.orbit ? (sequence % 2 == 0 ? -0.9 : 0.9) : (sequence % 2 == 0 ? -0.25 : 0.25);
        Vec3 origin = FollowAnchor.origin(camera.getPosition(), camera.getYRot(), config.distance, side);
        // Fit to about five blocks; wrapping also accommodates translation and resource pack fonts.
        int maxWidth = (int) Math.clamp(4.8 / config.textScale, 80, 300);
        var rows = client.font.split(Component.literal(text), maxWidth);
        if (rows.size() > 4) rows = rows.subList(0, 4);
        FloatingLyric bubble = new FloatingLyric(List.copyOf(rows), origin, right, forward,
                visualTime, lifetime, sequence++ * 1.73, config.orbit, config.color, side);
        bubbles.add(bubble);
        while (bubbles.size() > config.maxLines) bubbles.removeFirst();
        if (config.particles) {
            Vector3f color = new Vector3f(((config.color >> 16) & 255) / 255f, ((config.color >> 8) & 255) / 255f, (config.color & 255) / 255f);
            DustParticleOptions dust = new DustParticleOptions(color, 0.7f);
            for (int i = 0; i < 14; i++) {
                double angle = i * Math.PI * 2 / 14;
                Vec3 point = origin.add(right.scale(Math.cos(angle) * 1.1)).add(0, Math.sin(angle) * 0.25, 0);
                client.level.addParticle(dust, point.x, point.y, point.z, 0, 0.025, 0);
            }
        }
    }

    @Override public void close() { bridge.close(); lyrics.close(); }
}

