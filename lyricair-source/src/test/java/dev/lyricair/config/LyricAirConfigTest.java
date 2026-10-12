package dev.lyricair.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LyricAirConfigTest {
    @TempDir Path directory;
    @Test void upgradesOldConfigWithoutChangingWorldStyle() throws Exception {
        Path path = directory.resolve("lyricair.json");
        Files.writeString(path, "{\"color\":6752511,\"distance\":7,\"orbit\":true}");
        var config = LyricAirConfig.load(path);
        assertEquals(LyricAirConfig.DisplayMode.WORLD, config.displayMode);
        assertFalse(config.followPlayer);
        assertEquals(6752511, config.color);
        assertEquals(7, config.distance);
        assertTrue(config.orbit);
        assertTrue(config.albumArt && config.upcomingLyrics);
    }
    @Test void repairsUnknownModesAndBoundsOverlaySettings() throws Exception {
        Path path = directory.resolve("lyricair.json");
        Files.writeString(path, "{\"displayMode\":\"unknown\",\"overlayAnchor\":null,\"overlayScale\":99,\"overlayX\":-4,\"overlayOpacity\":0}");
        var config = LyricAirConfig.load(path);
        assertEquals(LyricAirConfig.DisplayMode.WORLD, config.displayMode);
        assertEquals(LyricAirConfig.OverlayAnchor.TOP_LEFT, config.overlayAnchor);
        assertEquals(1.5, config.overlayScale); assertEquals(0, config.overlayX); assertEquals(0.2, config.overlayOpacity);
        config.followPlayer = true; config.displayMode = LyricAirConfig.DisplayMode.BOTH; config.save(path);
        assertTrue(LyricAirConfig.load(path).followPlayer);
        assertEquals(LyricAirConfig.DisplayMode.BOTH, LyricAirConfig.load(path).displayMode);
    }
}
