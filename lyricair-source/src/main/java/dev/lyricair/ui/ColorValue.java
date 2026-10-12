package dev.lyricair.ui;

import java.util.OptionalInt;

public final class ColorValue {
    private ColorValue() {}
    public static OptionalInt parse(String input) {
        String value = input.strip();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}")) return OptionalInt.empty();
        return OptionalInt.of(Integer.parseInt(value, 16));
    }
    public static String hex(int color) { return "#%06X".formatted(color & 0xFFFFFF); }
    public static int channel(int color, int shift, int value) {
        return (color & ~(255 << shift)) | Math.clamp(value, 0, 255) << shift;
    }
}
