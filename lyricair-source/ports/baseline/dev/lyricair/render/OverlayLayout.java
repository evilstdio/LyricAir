package dev.lyricair.render;

import dev.lyricair.config.LyricAirConfig;

/** Normalized custom placement survives window size and GUI scale changes. */
public record OverlayLayout(int x, int y, int width, int height, double scale) {
    public static OverlayLayout calculate(int screenWidth, int screenHeight, int cardHeight, LyricAirConfig config) {
        int margin = 12;
        double scale = Math.min(config.overlayScale, Math.min(Math.max(1, screenWidth - margin * 2) / 280.0,
                Math.max(1, screenHeight - margin * 2) / (double) cardHeight));
        int width = (int) Math.ceil(280 * scale), height = (int) Math.ceil(cardHeight * scale);
        int rangeX = Math.max(0, screenWidth - margin * 2 - width), rangeY = Math.max(0, screenHeight - margin * 2 - height);
        double x = switch (config.overlayAnchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> 1;
            case CUSTOM -> config.overlayX;
            default -> 0;
        };
        double y = switch (config.overlayAnchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> 1;
            case CUSTOM -> config.overlayY;
            default -> 0;
        };
        return new OverlayLayout(margin + (int) Math.round(rangeX * x), margin + (int) Math.round(rangeY * y), width, height, scale);
    }
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    public static double normalized(double coordinate, int screenSize, int cardSize) {
        int range = screenSize - 24 - cardSize;
        return range <= 0 ? 0 : Math.clamp((coordinate - 12) / range, 0, 1);
    }
}
