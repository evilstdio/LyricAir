package dev.lyricair.render;

import dev.lyricair.LyricController;
import dev.lyricair.config.LyricAirConfig;
import dev.lyricair.playback.WindowsSpotifyBridge;
import dev.lyricair.ui.UiDraw;
import dev.lyricair.ui.VectorCanvas;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import static dev.lyricair.ui.VectorCanvas.*;

public final class LyricHudRenderer implements AutoCloseable {
    private final LyricController controller;
    private final LyricAirConfig config;
    private WindowsSpotifyBridge.Artwork loaded;
    private int coverImage;
    public record Content(String title, String artist, String active, List<String> upcoming,
                          long position, long duration, boolean playing, boolean preview) {}
    public LyricHudRenderer(LyricController controller, LyricAirConfig config) { this.controller = controller; this.config = config; }
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (!config.enabled || !config.displayMode.overlay() || client.options.hideGui || client.level == null
                || client.screen != null || controller.track() == null) return;
        drawAtLayout(context, content(false), false);
    }
    public Content content(boolean previewIfEmpty) {
        var track = controller.track();
        if (track == null && previewIfEmpty) return new Content("Floating echoes", "LyricAir demo", "Let the words take flight",
                List.of("A little light above the trees", "Turn the night into a skyline", "Every line becomes a constellation"), 42_000, 168_000, true, true);
        if (track == null) return new Content("Spotify desktop", "Waiting for music", "Play a song in Spotify", List.of(), 0, 0, false, false);
        var timeline = controller.timeline(); int index = timeline.indexAt(controller.lyricPosition());
        String active = index >= 0 ? timeline.lines().get(index).text() : "";
        if (active.isBlank()) active = timeline.lines().isEmpty() ? controller.status() : "♪ Instrumental break";
        List<String> upcoming = new ArrayList<>();
        for (int i = index + 1; i < timeline.lines().size() && upcoming.size() < 3; i++) {
            String line = timeline.lines().get(i).text(); if (!line.isBlank()) upcoming.add(line);
        }
        return new Content(track.title(), track.artist(), active, List.copyOf(upcoming), controller.position(), track.durationMs(), track.playing(), false);
    }
    // Minecraft supplies its Unicode fallback for scripts outside Inter's coverage.
    private static boolean fallback(String value) {
        return value.codePoints().anyMatch(code -> code > 0x052F && !(code >= 0x1E00 && code <= 0x20AC));
    }
    private static float measured(String value, float size) {
        if (!VectorCanvas.ready() || fallback(value)) return UiDraw.font().width(UiDraw.text(value)) * size / 11;
        return VectorCanvas.width(value, size);
    }
    private static List<String> rows(String value, float limit, float size) {
        List<String> rows = new ArrayList<>(); StringBuilder line = new StringBuilder();
        // Prefer spaces but support songs without word separators as well.
        for (String word : value.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (measured(candidate, size) > limit && !line.isEmpty()) { rows.add(line.toString()); line.setLength(0); }
            if (!line.isEmpty()) line.append(' ');
            for (int start = 0; start < word.length();) {
                int end = word.offsetByCodePoints(start, 1); String character = word.substring(start, end);
                if (measured(line + character, size) > limit && !line.isEmpty()) { rows.add(line.toString()); line.setLength(0); }
                line.append(character); start = end;
            }
        }
        if (!line.isEmpty()) rows.add(line.toString());
        return rows.subList(0, Math.min(2, rows.size()));
    }
    public int height(Content content) { return 97 + rows(content.active(), 252, 13).size() * 17 + (config.upcomingLyrics ? content.upcoming().size() * 16 + 9 : 0); }
    public OverlayLayout layout(GuiGraphics context, Content content) {
        return OverlayLayout.calculate(context.guiWidth(), context.guiHeight(), height(content), config);
    }
    /** Called while the vector frame is active. Caches one bounded PNG, including in menu previews. */
    public int cover(Content content) {
        var artwork = content.preview() ? null : controller.artwork();
        if (loaded != artwork) {
            VectorCanvas.deleteImage(coverImage); coverImage = 0; loaded = artwork;
            if (artwork != null) coverImage = VectorCanvas.image(artwork.png());
        }
        return coverImage;
    }
    public void drawAtLayout(GuiGraphics context, Content content, boolean edit) {
        VectorCanvas.begin(context);
        OverlayLayout layout;
        try {
            layout = layout(context, content); translate((float) layout.x(), (float) layout.y()); VectorCanvas.scale((float) layout.scale());
            drawCard(content);
            if (edit) border(0, 0, 280, height(content), 8, 0xFF000000 | config.color);
        } finally { VectorCanvas.end(); }
        // Draw Unicode text through Minecraft after the vector background and restore its exact transform.
        context.pose().pushPose(); context.pose().translate(layout.x(), layout.y(), 0); context.pose().scale((float) layout.scale(), (float) layout.scale(), 1);
        drawFallback(context, content); context.pose().popPose(); context.flush();
    }
    private void drawCard(Content content) {
        int accent = 0xFF000000 | config.color, height = height(content);
        rect(0, 3, 280, height, 8, 0x33000000); rect(0, 0, 280, height, 8, UiDraw.alpha(0x20262D, config.overlayOpacity));
        border(0, 0, 280, height, 8, UiDraw.alpha(0x667383, config.overlayOpacity * .45));
        int textX = config.albumArt ? 62 : 14;
        if (config.albumArt) {
            int cover = cover(content);
            if (cover > 0) VectorCanvas.image(cover, 12, 12, 38, 38, 5);
            else { rect(12, 12, 38, 38, 5, UiDraw.mix(0x2B333D, config.color, .12)); icon("music", 19, 19, accent); }
        }
        string(content.title(), textX, 11, 266 - textX, 12, 0xFFF5F7FA, BOLD);
        string(content.artist(), textX, 28, 266 - textX, 10.5f, 0xFFC3CBD5, REGULAR);
        for (int i = 0; i < 3; i++) {
            float barHeight = content.playing() ? 3 + (float) Math.abs(Math.sin(System.nanoTime() / 250_000_000.0 + i)) * 5 : 3;
            rect(textX + i * 4, 52 - barHeight, 2, barHeight, 1, accent);
        }
        text(content.preview() ? "PREVIEW" : content.playing() ? "PLAYING" : "PAUSED", textX + 16, 44, 8, 0xFF9AA6B2, BOLD);
        String time = timestamp(content.position()) + " / " + timestamp(content.duration());
        text(time, 266 - VectorCanvas.width(time, 9, MONO), 43, 9, 0xFFC3CBD5, MONO);
        rect(14, 64, 252, 3, 1.5f, 0xFF46515E);
        float progress = content.duration() > 0 ? (float) Math.clamp(content.position() / (double) content.duration(), 0, 1) : 0;
        rect(14, 64, 252 * progress, 3, 1.5f, accent); text("LYRICS", 14, 80, 8.5f, 0xFF9AA6B2, BOLD);
        List<String> rows = rows(content.active(), 252, 13);
        for (int i = 0; i < rows.size(); i++) string(rows.get(i), 14, 97 + i * 17, 252, 13, accent, REGULAR);
        if (config.upcomingLyrics) {
            int y = 97 + rows.size() * 17 + 8;
            for (int i = 0; i < content.upcoming().size(); i++) string(content.upcoming().get(i), 14, y + i * 16, 252, 10.5f, upcomingColor(i), REGULAR);
        }
    }
    private static int upcomingColor(int index) { return UiDraw.mix(0x20262D, 0xFFC3CBD5, .75 - index * .17); }
    private static void string(String value, int x, int y, int width, float size, int color, String face) {
        if (!fallback(value)) text(ellipsis(value, width, size), x, y, size, color, face);
    }
    private void drawFallback(GuiGraphics context, Content content) {
        int tx = config.albumArt ? 62 : 14;
        fallbackText(context, content.title(), tx, 11, 266 - tx, 12, 0xFFF5F7FA);
        fallbackText(context, content.artist(), tx, 28, 266 - tx, 10.5f, 0xFFC3CBD5);
        List<String> rows = rows(content.active(), 252, 13);
        for (int i = 0; i < rows.size(); i++) fallbackText(context, rows.get(i), 14, 97 + i * 17, 252, 13, 0xFF000000 | config.color);
        if (config.upcomingLyrics) for (int i = 0; i < content.upcoming().size(); i++) fallbackText(context, content.upcoming().get(i), 14, 105 + rows.size() * 17 + i * 16, 252, 10.5f, upcomingColor(i));
    }
    private static void fallbackText(GuiGraphics context, String value, int x, int y, int width, float size, int color) {
        if (!fallback(value)) return;
        context.pose().pushPose(); context.pose().translate(x, y, 0); context.pose().scale(size / 11, size / 11, 1);
        UiDraw.clipped(context, value, 0, 0, (int) (width * 11 / size), color); context.pose().popPose();
    }
    private static String timestamp(long ms) { long seconds = Math.max(0, ms) / 1_000; return "%d:%02d".formatted(seconds / 60, seconds % 60); }
    @Override public void close() { VectorCanvas.deleteImage(coverImage); coverImage = 0; loaded = null; }
}
