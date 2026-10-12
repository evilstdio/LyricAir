package dev.lyricair.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lyricair.LyricAirClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LyricAirConfig {
    public enum DisplayMode {
        WORLD("3D world"), OVERLAY("2D overlay"), BOTH("Both");
        public final String label;
        DisplayMode(String label) { this.label = label; }
        public boolean world() { return this != OVERLAY; }
        public boolean overlay() { return this != WORLD; }
    }
    public enum OverlayAnchor {
        TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right"), CUSTOM("Custom position");
        public final String label;
        OverlayAnchor(String label) { this.label = label; }
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean enabled = true;
    public boolean particles = true;
    public boolean orbit = false;
    public double distance = 5.0;
    public double textScale = 0.027;
    public int timingOffsetMs = 0;
    public int maxLines = 4;
    public int color = 0x8CFFBF;
    public DisplayMode displayMode = DisplayMode.WORLD;
    public boolean followPlayer = false;
    public OverlayAnchor overlayAnchor = OverlayAnchor.TOP_LEFT;
    public double overlayScale = 0.8;
    public double overlayOpacity = 0.88;
    public double overlayX = 0;
    public double overlayY = 0;
    public boolean albumArt = true;
    public boolean upcomingLyrics = true;

    public static LyricAirConfig load(Path path) {
        try {
            if (Files.exists(path)) {
                LyricAirConfig config = GSON.fromJson(Files.readString(path), LyricAirConfig.class);
                if (config != null) { config.validate(); return config; }
            }
        } catch (IOException | RuntimeException e) {
            LyricAirClient.LOGGER.warn("Could not read LyricAir config; using defaults", e);
        }
        return new LyricAirConfig();
    }

    public void save(Path path) {
        validate();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) { LyricAirClient.LOGGER.warn("Could not save LyricAir config", e); }
    }

    private void validate() {
        distance = Double.isFinite(distance) ? Math.clamp(distance, 2.5, 12) : 5;
        textScale = Double.isFinite(textScale) ? Math.clamp(textScale, 0.012, 0.06) : 0.027;
        timingOffsetMs = Math.clamp(timingOffsetMs, -10_000, 10_000);
        maxLines = Math.clamp(maxLines, 1, 6);
        color &= 0xFFFFFF;
        if (displayMode == null) displayMode = DisplayMode.WORLD;
        if (overlayAnchor == null) overlayAnchor = OverlayAnchor.TOP_LEFT;
        overlayScale = finiteClamp(overlayScale, 0.55, 1.5, 0.8);
        overlayOpacity = finiteClamp(overlayOpacity, 0.2, 1, 0.88);
        overlayX = finiteClamp(overlayX, 0, 1, 0);
        overlayY = finiteClamp(overlayY, 0, 1, 0);
    }
    private static double finiteClamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
