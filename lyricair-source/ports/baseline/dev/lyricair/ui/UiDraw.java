package dev.lyricair.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Shared visual language for the menu and HUD. */
public final class UiDraw {
    public static final int BACKGROUND = 0xFF111821, CARD = 0xFF1B2531, BORDER = 0xFF2B3948;
    public static final int TEXT = 0xFFE9F1F5, MUTED = 0xFF97AABA, DIM = 0xFF607788;
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("lyricair", "ui");
    private UiDraw() {}
    public static Component text(String value) { return Component.literal(value).withStyle(style -> style.withFont(FONT)); }
    public static Font font() { return Minecraft.getInstance().font; }
    public static void label(GuiGraphics context, String value, int x, int y, int color) {
        context.drawString(font(), text(value), x, y, color, false);
    }
    public static void centered(GuiGraphics context, String value, int x, int y, int color) {
        label(context, value, x - font().width(text(value)) / 2, y, color);
    }
    public static void clipped(GuiGraphics context, String value, int x, int y, int width, int color) {
        label(context, ellipsis(value, width), x, y, color);
    }
    public static String ellipsis(String value, int width) {
        if (font().width(text(value)) <= width) return value;
        int low = 0, high = value.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (font().width(text(value.substring(0, middle) + "…")) <= width) low = middle;
            else high = middle - 1;
        }
        if (low > 0 && Character.isHighSurrogate(value.charAt(low - 1))) low--;
        return value.substring(0, low) + "…";
    }
    public static void rounded(GuiGraphics context, int x, int y, int width, int height, int radius, int color) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        context.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            context.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }
    public static void card(GuiGraphics context, int x, int y, int width, int height, int fill, int border) {
        rounded(context, x, y, width, height, 8, border);
        rounded(context, x + 1, y + 1, width - 2, height - 2, 7, fill);
    }
    public static int alpha(int rgb, double alpha) { return ((int) Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF); }
    public static int mix(int first, int second, double amount) {
        int r = (int) (((first >> 16) & 255) * (1 - amount) + ((second >> 16) & 255) * amount);
        int g = (int) (((first >> 8) & 255) * (1 - amount) + ((second >> 8) & 255) * amount);
        int b = (int) ((first & 255) * (1 - amount) + (second & 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
    public static void musicIcon(GuiGraphics context, int x, int y, int size, int color) {
        int unit = Math.max(1, size / 10);
        context.fill(x + 4 * unit, y + unit, x + 5 * unit, y + 7 * unit, color);
        context.fill(x + 8 * unit, y, x + 9 * unit, y + 6 * unit, color);
        context.fill(x + 4 * unit, y + unit, x + 9 * unit, y + 3 * unit, color);
        rounded(context, x + unit, y + 6 * unit, 4 * unit, 3 * unit, unit, color);
        rounded(context, x + 5 * unit, y + 5 * unit, 4 * unit, 3 * unit, unit, color);
    }
}
