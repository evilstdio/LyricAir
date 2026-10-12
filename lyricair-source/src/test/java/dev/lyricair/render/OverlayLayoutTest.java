package dev.lyricair.render;

import dev.lyricair.config.LyricAirConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverlayLayoutTest {
    @Test void cornersKeepTheCardInsideSmallWindows() {
        var config = new LyricAirConfig(); config.overlayScale = 1.5;
        for (var anchor : LyricAirConfig.OverlayAnchor.values()) {
            config.overlayAnchor = anchor;
            var layout = OverlayLayout.calculate(200, 150, 160, config);
            assertTrue(layout.x() >= 12 && layout.y() >= 12);
            assertTrue(layout.x() + layout.width() <= 189 && layout.y() + layout.height() <= 139);
        }
    }
    @Test void normalizedDraggingSurvivesResizing() {
        var config = new LyricAirConfig(); config.overlayAnchor = LyricAirConfig.OverlayAnchor.CUSTOM;
        config.overlayX = 0.6; config.overlayY = 0.3;
        for (int width : new int[]{427, 960}) {
            var layout = OverlayLayout.calculate(width, 540, 160, config);
            assertEquals(0.6, OverlayLayout.normalized(layout.x(), width, layout.width()), 0.006);
            assertEquals(0.3, OverlayLayout.normalized(layout.y(), 540, layout.height()), 0.006);
        }
    }
}
