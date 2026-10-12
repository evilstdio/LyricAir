package dev.lyricair.ui;

import dev.lyricair.LyricController;
import dev.lyricair.config.LyricAirConfig;
import dev.lyricair.render.LyricHudRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import static dev.lyricair.ui.VectorCanvas.*;

public final class LyricAirScreen extends Screen {
    private enum Tab {
        DISPLAY("Display", "Choose your lyric display", "display"), WORLD("World", "Floating text and movement", "world"),
        OVERLAY("Overlay", "Now playing, on your screen", "overlay"), COLOR("Color", "Customize your lyric color", "color");
        final String title, description, icon;
        Tab(String title, String description, String icon) { this.title = title; this.description = description; this.icon = icon; }
    }
    public static final int PANEL_WIDTH = 760, PANEL_HEIGHT = 430;
    private static final int X = 80, W = 440;
    private static final int BG = 0xFF20262D, SIDEBAR = 0xFF171C23, CARD = 0xFF2B333D, FIELD = 0xFF36404B;
    private static final int TEXT = 0xFFF5F7FA, MUTED = 0xFFC3CBD5, DIM = 0xFF9AA6B2, BORDER = 0x204A5563, ACCENT = 0xFFD32F2F;
    private static final int[] PRESETS = {0x8CFFBF, 0x67C8FF, 0xC6A5FF, 0xFF95BA, 0xFFD07A, 0xFFFFFF};
    private record SearchResult(String label, Tab tab) {}
    private static final List<SearchResult> INDEX = List.of(
            new SearchResult("Enable LyricAir", Tab.DISPLAY), new SearchResult("Display mode · World / Overlay / Both", Tab.DISPLAY), new SearchResult("Lyric timing", Tab.DISPLAY),
            new SearchResult("Follow player", Tab.WORLD), new SearchResult("Movement · Float / Orbit", Tab.WORLD), new SearchResult("Particles", Tab.WORLD), new SearchResult("Distance", Tab.WORLD), new SearchResult("Text size", Tab.WORLD),
            new SearchResult("Overlay position", Tab.OVERLAY), new SearchResult("Overlay size", Tab.OVERLAY), new SearchResult("Background opacity", Tab.OVERLAY), new SearchResult("Album artwork", Tab.OVERLAY), new SearchResult("Upcoming lyrics", Tab.OVERLAY), new SearchResult("Move overlay", Tab.OVERLAY),
            new SearchResult("Color presets", Tab.COLOR), new SearchResult("Custom hex color", Tab.COLOR), new SearchResult("Red", Tab.COLOR), new SearchResult("Green", Tab.COLOR), new SearchResult("Blue", Tab.COLOR));
    private final Screen parent;
    private final LyricAirConfig config;
    private final LyricController controller;
    private final LyricHudRenderer hud;
    private final Runnable saveConfig;
    private final List<ClickableWidget> settings = new ArrayList<>();
    private Tab tab = Tab.DISPLAY;
    private float scale, sidebarWidth = 60;
    private int left, top, searchOffset;
    private int contentX = X, contentWidth = W;
    private Button demoButton, reconnectButton;
    private SmoothField search, hex;
    private long lastFrame = System.nanoTime();

    public LyricAirScreen(Screen parent, LyricAirConfig config, LyricController controller, LyricHudRenderer hud, Runnable save) {
        super(Text.literal("LyricAir")); this.parent = parent; this.config = config; this.controller = controller; this.hud = hud; this.saveConfig = save;
    }
    @Override protected void init() {
        scale = Math.min(1, Math.min((width - 16f) / PANEL_WIDTH, (height - 16f) / PANEL_HEIGHT));
        left = (int) ((width - PANEL_WIDTH * scale) / 2); top = (int) ((height - PANEL_HEIGHT * scale) / 2);
        settings.clear(); hex = null;
        search = new SmoothField(540, 12, 200, 30, "Search settings...", false); search.setMaxLength(48);
        search.setChangedListener(value -> { searchOffset = 0; settings.forEach(widget -> widget.visible = value.isBlank()); }); addDrawableChild(search);
        for (Tab item : Tab.values()) addDrawableChild(new Nav(item));
        demoButton = addDrawableChild(new Button(80, 387, 110, 28, () -> controller.demo() ? "Stop demo" : "Try demo", controller::demo, () -> { controller.toggleDemo(); saveConfig.run(); }));
        reconnectButton = addDrawableChild(new Button(198, 387, 142, 28, () -> "Reconnect Spotify", () -> false, controller::reconnect));
        addDrawableChild(new Button(670, 387, 70, 28, () -> "Done", () -> true, this::close));
        switch (tab) {
            case DISPLAY -> {
                toggle(124, "Enable LyricAir", "Show lyrics while you play", () -> config.enabled, () -> config.enabled = !config.enabled, true);
                for (var mode : LyricAirConfig.DisplayMode.values()) setting(new ModeButton(X + mode.ordinal() * 150, 207, mode));
                slider(292, "Lyric timing", () -> (config.timingOffsetMs + 5_000) / 10_000.0,
                        value -> config.timingOffsetMs = (int) Math.round((value * 10_000 - 5_000) / 50) * 50,
                        () -> "%+.2f s".formatted(config.timingOffsetMs / 1000.0), true);
            }
            case WORLD -> {
                toggle(124, "Follow player", "Keep lyrics in front of your view", () -> config.followPlayer, () -> config.followPlayer = !config.followPlayer, true);
                setting(new Choice(176, "Movement", () -> config.orbit ? "Orbit" : "Float", () -> { config.orbit = !config.orbit; changed(true); }));
                toggle(228, "Particles", "A small burst with each new line", () -> config.particles, () -> config.particles = !config.particles, false);
                slider(280, "Distance", () -> (config.distance - 2.5) / 9.5, value -> config.distance = 2.5 + value * 9.5, () -> "%.1f m".formatted(config.distance), true);
                slider(332, "Text size", () -> (config.textScale - 0.012) / 0.048, value -> config.textScale = 0.012 + value * 0.048, () -> "%.0f%%".formatted(config.textScale / 0.027 * 100), true);
            }
            case OVERLAY -> {
                setting(new Choice(124, "Position", () -> config.overlayAnchor.label, () -> { config.overlayAnchor = LyricAirConfig.OverlayAnchor.values()[(config.overlayAnchor.ordinal() + 1) % 4]; saveConfig.run(); }));
                slider(176, "Overlay size", () -> (config.overlayScale - 0.55) / 0.95, value -> config.overlayScale = 0.55 + value * 0.95, () -> "%.0f%%".formatted(config.overlayScale * 100), false);
                slider(228, "Background opacity", () -> (config.overlayOpacity - 0.2) / 0.8, value -> config.overlayOpacity = 0.2 + value * 0.8, () -> "%.0f%%".formatted(config.overlayOpacity * 100), false);
                toggle(280, "Album artwork", "Read the cover from Spotify desktop", () -> config.albumArt, () -> config.albumArt = !config.albumArt, false);
                toggle(332, "Upcoming lyrics", "Show the next three lines", () -> config.upcomingLyrics, () -> config.upcomingLyrics = !config.upcomingLyrics, false);
                addDrawableChild(new Button(558, 330, 164, 30, () -> "Move overlay…", () -> false, () -> client.setScreen(new OverlayEditorScreen(this, config, hud, saveConfig))));
            }
            case COLOR -> {
                for (int i = 0; i < PRESETS.length; i++) setting(new Swatch(X + i * 75, 132, PRESETS[i]));
                hex = new SmoothField(324, 205, 180, 32, "#RRGGBB", true); hex.setMaxLength(7); hex.setText(ColorValue.hex(config.color));
                hex.setChangedListener(value -> ColorValue.parse(value).ifPresent(color -> { config.color = color; saveConfig.run(); })); setting(hex);
                for (int i = 0; i < 3; i++) {
                    int shift = (2 - i) * 8; String label = new String[]{"Red", "Green", "Blue"}[i];
                    slider(254 + i * 42, label, () -> ((config.color >> shift) & 255) / 255.0, value -> {
                        config.color = ColorValue.channel(config.color, shift, (int) Math.round(value * 255));
                        if (!hex.isFocused()) hex.setText(ColorValue.hex(config.color));
                    }, () -> Integer.toString((config.color >> shift) & 255), false, 36);
                }
            }
        }
    }
    private void setting(ClickableWidget widget) { settings.add(widget); addDrawableChild(widget); }
    private void changed(boolean respawn) { if (respawn) controller.clearVisuals(); saveConfig.run(); }
    private void select(Tab selected) { tab = selected; clearAndInit(); }
    private void toggle(int y, String label, String description, BooleanSupplier value, Runnable action, boolean respawn) {
        setting(new Toggle(y, label, description, value, () -> { action.run(); changed(respawn); }));
    }
    private void slider(int y, String label, DoubleSupplier normalized, DoubleConsumer change, Supplier<String> value, boolean respawn) { slider(y, label, normalized, change, value, respawn, 44); }
    private void slider(int y, String label, DoubleSupplier normalized, DoubleConsumer change, Supplier<String> value, boolean respawn, int height) {
        setting(new SettingSlider(y, height, label, normalized, change, value, respawn));
    }
    private List<SearchResult> results() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        return INDEX.stream().filter(item -> (item.label + " " + item.tab.title).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private void layoutContent() {
        int shift = Math.round(sidebarWidth - 60); contentX = X + shift; contentWidth = W - shift;
        demoButton.setX(contentX); reconnectButton.setX(contentX + 118);
        for (ClickableWidget widget : settings) {
            if (widget instanceof ModeButton mode) {
                int cardWidth = (contentWidth - 20) / 3;
                widget.setX(contentX + mode.mode.ordinal() * (cardWidth + 10)); widget.setWidth(cardWidth);
            } else if (widget instanceof Swatch swatch) {
                int swatchWidth = (contentWidth - 50) / 6, index = 0;
                while (PRESETS[index] != swatch.color) index++;
                widget.setX(contentX + index * (swatchWidth + 10)); widget.setWidth(swatchWidth);
            } else if (widget == hex) widget.setX(contentX + contentWidth - 196);
            else { widget.setX(contentX); widget.setWidth(contentWidth); }
        }
    }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x80000000);
        VectorCanvas.begin(context);
        try {
            translate(left, top); VectorCanvas.scale(scale);
            float mx = (float) localX(mouseX), my = (float) localY(mouseY);
            long now = System.nanoTime(); float elapsed = Math.min(.1f, (now - lastFrame) / 1_000_000_000f); lastFrame = now;
            float goal = mx >= 0 && mx < sidebarWidth && my >= 0 && my < PANEL_HEIGHT ? 174 : 60;
            sidebarWidth += (goal - sidebarWidth) * (1 - (float) Math.exp(-elapsed * 16));
            layoutContent();
            for (int i = 16; i > 0; i--) rect(-i, -i + 4, PANEL_WIDTH + 2 * i, PANEL_HEIGHT + 2 * i, 10 + i, 0x03000000);
            rect(0, 0, PANEL_WIDTH, PANEL_HEIGHT, 10, BG); border(0, 0, PANEL_WIDTH, PANEL_HEIGHT, 10, 0x50667383);
            rect(60, 52, 700, 1, 0, 0x604A5563); rect(60, 89, 700, 1, 0, 0x404A5563);
            text(tab.title, contentX, 19, 15, TEXT, BOLD);
            float separator = contentX + VectorCanvas.width(tab.title, 15, BOLD) + 16;
            rect(separator, 17, 1, 21, 0, 0xFF4A5563); text(tab.description, separator + 14, 21, 11.5f, DIM);
            text("Settings", contentX, 65, 12, TEXT); rect(contentX, 86, 48, 2, 1, ACCENT);
            circle(672, 71, 3, config.enabled ? 0xFF4CAF50 : DIM); text(config.enabled ? "Enabled" : "Disabled", 682, 65, 11, MUTED);
            boolean searching = !search.getText().isBlank(); settings.forEach(widget -> widget.visible = !searching);
            if (searching) drawResults(); else drawSection();
            drawPreview();
            rect(contentX, 380, 740 - contentX, 1, 0, 0x604A5563);
            text("Saved automatically", 535, 398, 10.5f, DIM);
            super.render(context, mx < sidebarWidth ? -1000 : (int) mx, (int) my, delta);
            drawSidebar(mx, my);
        } finally { VectorCanvas.end(); }
    }
    private void section(String label, String description) {
        text(label, contentX, 105, 10, ACCENT, BOLD);
        float end = contentX + VectorCanvas.width(label, 10, BOLD) + 12;
        description = ellipsis(description, contentWidth - (end - contentX), 10.5f);
        text(description, end, 104, 10.5f, DIM);
        float divider = end + VectorCanvas.width(description, 10.5f) + 12;
        rect(divider, 110, contentX + contentWidth - divider, 1, 0, 0x604A5563);
    }
    private void drawSection() {
        switch (tab) {
            case DISPLAY -> {
                section("GENERAL", "Your music, in your world");
                text("DISPLAY MODE", contentX, 187, 10, ACCENT, BOLD); rect(contentX + 97, 193, contentWidth - 97, 1, 0, 0x604A5563);
                text(switch (config.displayMode) {
                    case WORLD -> "Lyrics float in the world around you.";
                    case OVERLAY -> "Track info and lyrics stay on your screen.";
                    case BOTH -> "Floating lyrics and the overlay, together.";
                }, contentX + 2, 267, 11, DIM);
                text(ellipsis("Positive timing values bring lyrics in earlier.", contentWidth, 11), contentX + 2, 346, 11, DIM);
            }
            case WORLD -> section("WORLD LYRICS", "Text, movement & effects");
            case OVERLAY -> section("ON-SCREEN OVERLAY", "Layout & appearance");
            case COLOR -> {
                section("TEXT COLOR", "World lyrics, particles & overlay");
                card(contentX, 196, contentWidth, 50, false, false); text("Custom color", contentX + 16, 215, 12.5f, TEXT);
                if (ColorValue.parse(hex.getText()).isEmpty()) text("Use six hexadecimal digits", contentX + 2, 372, 10, 0xFFFF9393);
            }
        }
    }
    private void drawResults() {
        section("SEARCH", "Matching settings");
        var results = results(); searchOffset = Math.clamp(searchOffset, 0, Math.max(0, results.size() - 5));
        for (int i = searchOffset; i < Math.min(results.size(), searchOffset + 5); i++) {
            var result = results.get(i); int y = 124 + (i - searchOffset) * 48;
            card(contentX, y, contentWidth, 40, false, false); text(ellipsis(result.label, contentWidth - 100, 12.5f), contentX + 16, y + 13, 12.5f, TEXT);
            text(result.tab.title, contentX + contentWidth - VectorCanvas.width(result.tab.title, 11) - 16, y + 14, 11, DIM);
        }
        if (results.isEmpty()) text("No settings match this search.", contentX + 16, 145, 12.5f, DIM);
        if (results.size() > 5) text("Scroll to see more results", contentX + 2, 371, 10, DIM);
    }
    private void drawSidebar(float mx, float my) {
        save(); clip(0, 0, sidebarWidth, PANEL_HEIGHT);
        rect(0, 0, sidebarWidth, PANEL_HEIGHT, 10, SIDEBAR); rect(sidebarWidth - 10, 0, 10, PANEL_HEIGHT, 0, SIDEBAR);
        rect(sidebarWidth - 1, 12, 1, PANEL_HEIGHT - 24, 0, 0x304A5563);
        icon("music", 18, 17, ACCENT); text("LyricAir", 62, 21, 15, TEXT, BOLD); rect(12, 56, sidebarWidth - 24, 1, 0, 0x604A5563);
        for (Tab item : Tab.values()) {
            int y = 76 + item.ordinal() * 44; boolean hovered = mx >= 12 && mx < sidebarWidth - 12 && my >= y && my < y + 36;
            rect(12, y, sidebarWidth - 24, 36, 8, tab == item ? 0x55D32F2F : hovered ? 0x3338414D : 0x00000000);
            icon(item.icon, 18, y + 6, tab == item ? ACCENT : hovered ? TEXT : DIM);
            text(item.title, 62, y + 12, 12.5f, tab == item ? TEXT : MUTED);
            if (children().stream().anyMatch(child -> child instanceof Nav nav && nav.target == item && nav.isFocused())) border(12, y, sidebarWidth - 24, 36, 8, 0xFF8795A5);
        }
        rect(12, 336, sidebarWidth - 24, 1, 0, 0x604A5563); icon("music", 18, 354, DIM);
        text("O  Settings", 62, 350, 11, MUTED); text("K  Toggle lyrics", 62, 369, 11, DIM);
        center("LA", 30, 398, 10, DIM); text("0.2.1", 62, 398, 10, DIM, MONO);
        restore();
    }
    private void drawPreview() {
        rect(540, 104, 200, 266, 7, 0xFF171C23); border(540, 104, 200, 266, 7, 0x304A5563);
        text("LIVE PREVIEW", 554, 118, 10, DIM, BOLD); circle(722, 124, 2.5f, 0xFF4CAF50);
        var content = hud.content(true); int color = 0xFF000000 | config.color;
        text("ON SCREEN", 554, 145, 9, DIM, BOLD);
        rect(554, 164, 172, 96, 6, 0xFF26303A); border(554, 164, 172, 96, 6, 0x304A5563);
        if (config.albumArt) {
            int cover = hud.cover(content);
            if (cover > 0) VectorCanvas.image(cover, 564, 175, 28, 28, 5);
            else { rect(564, 175, 28, 28, 5, UiDraw.mix(0x2B333D, config.color, .12)); icon("music", 566, 177, color); }
        }
        int tx = config.albumArt ? 600 : 564;
        text(ellipsis(content.title(), 716 - tx, 11), tx, 177, 11, TEXT, BOLD);
        text(ellipsis(content.artist(), 716 - tx, 9), tx, 192, 9, DIM);
        rect(564, 211, 152, 3, 1.5f, 0xFF46515E);
        float progress = content.duration() > 0 ? Math.clamp(content.position() / (float) content.duration(), 0, 1) : 0;
        rect(564, 211, 152 * progress, 3, 1.5f, color);
        text(ellipsis(content.active(), 152, 10.5f), 564, 224, 10.5f, color);
        if (config.upcomingLyrics && !content.upcoming().isEmpty()) text(ellipsis(content.upcoming().getFirst(), 152, 9), 564, 241, 9, DIM);
        text("IN THE WORLD", 554, 278, 9, DIM, BOLD);
        String sample = ellipsis(content.active(), 164, 12);
        text(sample, 557, 302, 12, UiDraw.mix(config.color, 0x171C23, .75)); text(sample, 555, 300, 12, color);
        if (tab != Tab.OVERLAY) text(ellipsis(controller.demo() ? "Demo · " + (config.followPlayer ? "following your view" : "anchored in the world") : controller.status(), 172, 10), 554, 347, 10, DIM);
    }
    private static void card(int x, int y, int width, int height, boolean hovered, boolean focused) {
        rect(x, y, width, height, 7, hovered ? 0xFF303945 : CARD); border(x, y, width, height, 7, focused ? 0xFF8795A5 : 0x1FFFFFFF);
    }
    private double localX(double x) { return (x - left) / scale; }
    private double localY(double y) { return (y - top) / scale; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        double mx = localX(x), my = localY(y);
        if (button == 0 && mx >= 12 && mx < sidebarWidth - 12) {
            for (Tab item : Tab.values()) if (my >= 76 + item.ordinal() * 44 && my < 112 + item.ordinal() * 44) { select(item); return true; }
        }
        if (!search.getText().isBlank() && button == 0 && mx >= contentX && mx <= contentX + contentWidth && my >= 124 && my < 364) {
            int index = searchOffset + (int) ((my - 124) / 48); var results = results();
            if (index < results.size()) { select(results.get(index).tab); return true; }
        }
        if (mx < sidebarWidth && my >= 0 && my < PANEL_HEIGHT) return false;
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(localX(x), localY(y), button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged(localX(x), localY(y), button, dx / scale, dy / scale); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!search.getText().isBlank()) { searchOffset = Math.clamp(searchOffset - (int) Math.signum(vertical), 0, Math.max(0, results().size() - 5)); return true; }
        return super.mouseScrolled(localX(x), localY(y), horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused() && key == GLFW.GLFW_KEY_ENTER && !search.getText().isBlank() && !results().isEmpty()) { select(results().getFirst().tab); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { saveConfig.run(); if (client != null) client.setScreen(parent); }

    private class Button extends ClickableWidget {
        private final Supplier<String> label; private final BooleanSupplier selected; private final Runnable action;
        Button(int x, int y, int width, int height, Supplier<String> label, BooleanSupplier selected, Runnable action) {
            super(x, y, width, height, Text.literal(label.get())); this.label = label; this.selected = selected; this.action = action;
        }
        @Override protected void renderWidget(DrawContext context, int mx, int my, float delta) {
            boolean chosen = selected.getAsBoolean(); setMessage(Text.literal(label.get()));
            rect(getX(), getY(), width, height, 7, chosen ? 0x55D32F2F : isHovered() ? 0xFF414B58 : FIELD);
            border(getX(), getY(), width, height, 7, chosen || isFocused() ? 0x99D32F2F : 0x30667383);
            center(label.get(), getX() + width / 2f, getY() + (height - 12) / 2f, 12, TEXT);
        }
        @Override public void onClick(double x, double y) { action.run(); }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER)) { action.run(); return true; } return false;
        }
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
    }
    private final class Nav extends Button {
        private final Tab target;
        Nav(Tab tab) { super(12, 76 + tab.ordinal() * 44, 36, 36, () -> tab.title, () -> LyricAirScreen.this.tab == tab, () -> select(tab)); target = tab; }
        @Override protected void renderWidget(DrawContext context, int mx, int my, float delta) { if (isFocused()) border(12, getY(), sidebarWidth - 24, 36, 8, 0xFF8795A5); }
    }
    private final class Toggle extends Button {
        private final String label, description; private final BooleanSupplier value;
        Toggle(int y, String label, String description, BooleanSupplier value, Runnable action) {
            super(X, y, W, 44, () -> label + (value.getAsBoolean() ? ": on" : ": off"), value, action); this.label = label; this.description = description; this.value = value;
        }
        @Override protected void renderWidget(DrawContext context, int mx, int my, float delta) {
            card(getX(), getY(), width, height, isHovered(), isFocused());
            text(label, getX() + 16, getY() + 7, 12.5f, TEXT); text(ellipsis(description, width - 78, 10.5f), getX() + 16, getY() + 25, 10.5f, DIM);
            int sx = getX() + width - 54; boolean on = value.getAsBoolean();
            rect(sx, getY() + 11.5f, 38, 21, 10.5f, on ? ACCENT : 0xFF46515E); circle(sx + (on ? 27.5f : 10.5f), getY() + 22, 7.5f, on ? TEXT : 0xFFB4BEC9);
            setMessage(Text.literal(label + (on ? ": on" : ": off")));
        }
    }
    private final class Choice extends Button {
        private final String label; private final Supplier<String> value;
        Choice(int y, String label, Supplier<String> value, Runnable action) { super(X, y, W, 44, () -> label + ": " + value.get(), () -> false, action); this.label = label; this.value = value; }
        @Override protected void renderWidget(DrawContext context, int mx, int my, float delta) {
            card(getX(), getY(), width, height, isHovered(), isFocused()); text(label, getX() + 16, getY() + 16, 12.5f, TEXT);
            rect(getX() + width - 178, getY() + 6, 162, 32, 7, FIELD); border(getX() + width - 178, getY() + 6, 162, 32, 7, 0x40667383);
            text(ellipsis(value.get(), 112, 12), getX() + width - 166, getY() + 16, 12, MUTED);
            rect(getX() + width - 49, getY() + 12, 1, 20, 0, 0x50667383); icon("chevron", getX() + width - 44, getY() + 10, DIM);
        }
    }
    private final class ModeButton extends Button {
        private final LyricAirConfig.DisplayMode mode;
        ModeButton(int x, int y, LyricAirConfig.DisplayMode mode) { super(x, y, 140, 50, () -> mode.label, () -> config.displayMode == mode, () -> { config.displayMode = mode; changed(true); }); this.mode = mode; }
        @Override protected void renderWidget(DrawContext context, int mx, int my, float delta) {
            boolean selected = config.displayMode == mode;
            rect(getX(), getY(), width, height, 7, selected ? 0x55D32F2F : isHovered() ? 0xFF38414D : CARD); border(getX(), getY(), width, height, 7, selected ? ACCENT : isFocused() ? 0xFF8795A5 : 0x1FFFFFFF);
            String glyph = mode == LyricAirConfig.DisplayMode.WORLD ? "world" : mode == LyricAirConfig.DisplayMode.OVERLAY ? "overlay" : "display";
            if (width < 125) { save(); translate(getX() + width / 2f - 9, getY() + 6); VectorCanvas.scale(.75f); icon(glyph, 0, 0, selected ? ACCENT : DIM); restore(); center(mode.label, getX() + width / 2f, getY() + 31, 10.5f, selected ? TEXT : MUTED); }
            else { icon(glyph, getX() + 14, getY() + 13, selected ? ACCENT : DIM); text(mode.label, getX() + 49, getY() + 19, 12.5f, selected ? TEXT : MUTED); }
        }
    }
    private final class Swatch extends Button {
        private final int color;
        Swatch(int x, int y, int color) { super(x, y, 65, 50, () -> ColorValue.hex(color), () -> config.color == color, () -> { config.color = color; hex.setText(ColorValue.hex(color)); saveConfig.run(); }); this.color = color; }
        @Override protected void renderWidget(DrawContext context, int mx, int my, float delta) {
            rect(getX(), getY(), width, height, 7, 0xFF000000 | color); border(getX(), getY(), width, height, 7, isFocused() || config.color == color ? TEXT : 0x33FFFFFF);
            if (config.color == color) icon("check", getX() + 20, getY() + 13, 0xFF20262D);
        }
    }
    private final class SettingSlider extends ClickableWidget {
        private final String label; private final DoubleSupplier normalized; private final DoubleConsumer change; private final Supplier<String> display; private final boolean respawn;
        private boolean dragging;
        SettingSlider(int y, int height, String label, DoubleSupplier normalized, DoubleConsumer change, Supplier<String> display, boolean respawn) {
            super(X, y, W, height, Text.literal(label)); this.label = label; this.normalized = normalized; this.change = change; this.display = display; this.respawn = respawn;
        }
        private int trackWidth() { return Math.clamp(width - 250, 78, 130); }
        private int trackX() { return getX() + width - 24 - trackWidth(); }
        private void change(double mouseX) { set((mouseX - trackX()) / trackWidth()); }
        private void set(double value) { change.accept(Math.clamp(value, 0, 1)); if (respawn) controller.clearVisuals(); setMessage(Text.literal(label + ": " + display.get())); }
        @Override public void onClick(double x, double y) { dragging = x >= trackX() - 6 && x <= trackX() + trackWidth() + 6; if (dragging) change(x); }
        @Override protected void onDrag(double x, double y, double dx, double dy) { if (dragging) change(x); }
        @Override public void onRelease(double x, double y) { dragging = false; saveConfig.run(); }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (isFocused() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) { set(normalized.getAsDouble() + (key == GLFW.GLFW_KEY_RIGHT ? .01 : -.01)); saveConfig.run(); return true; } return false;
        }
        @Override protected void renderWidget(DrawContext context, int mx, int my, float delta) {
            int fieldX = trackX() - 80; float cy = getY() + height / 2f;
            card(getX(), getY(), width, height, isHovered(), isFocused()); text(ellipsis(label, fieldX - getX() - 30, 12.5f), getX() + 16, getY() + (height - 12.5f) / 2, 12.5f, TEXT);
            rect(fieldX, cy - 14, 66, 28, 7, FIELD); border(fieldX, cy - 14, 66, 28, 7, 0x40667383);
            String number = display.get(); text(number, fieldX + (66 - VectorCanvas.width(number, 11.5f, MONO)) / 2, cy - 5.75f, 11.5f, MUTED, MONO);
            rect(trackX(), cy - 2, trackWidth(), 4, 2, 0xFF46515E); float amount = (float) Math.clamp(normalized.getAsDouble(), 0, 1);
            rect(trackX(), cy - 2, amount * trackWidth(), 4, 2, 0xFF4FA3D1); circle(trackX() + amount * trackWidth(), cy, 6, TEXT);
        }
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { setMessage(Text.literal(label + ": " + display.get())); appendDefaultNarrations(builder); }
    }
    private final class SmoothField extends TextFieldWidget {
        private final String placeholder; private final boolean mono; private int selection;
        SmoothField(int x, int y, int width, int height, String placeholder, boolean mono) {
            super(textRenderer, x, y, width, height, Text.literal(placeholder)); this.placeholder = placeholder; this.mono = mono;
            setDrawsBackground(false); setEditableColor(TEXT);
        }
        @Override public void setSelectionEnd(int cursor) { super.setSelectionEnd(cursor); selection = Math.clamp(cursor, 0, getText().length()); }
        @Override public void setText(String text) { super.setText(text); selection = getCursor(); }
        @Override public void write(String text) { super.write(text); selection = getCursor(); }
        @Override public void onClick(double mouseX, double mouseY) {
            int padding = mono ? 12 : 34, cursor = 0; float available = (float) mouseX - getX() - padding;
            String face = mono ? MONO : REGULAR;
            float caret = VectorCanvas.width(getText().substring(0, Math.clamp(getCursor(), 0, getText().length())), 12, face);
            available += Math.max(0, caret - (width - padding - 12));
            while (cursor < getText().length() && VectorCanvas.width(getText().substring(0, cursor + 1), 12, face) < available) cursor++;
            setCursor(cursor, Screen.hasShiftDown());
        }
        @Override public void renderWidget(DrawContext context, int mx, int my, float delta) {
            rect(getX(), getY(), width, height, 7, 0xFF323A45); border(getX(), getY(), width, height, 7, isFocused() ? 0xFF8795A5 : 0x40667383);
            if (!mono) { save(); translate(getX() + 9, getY() + 6); VectorCanvas.scale(.72f); icon("search", 0, 0, DIM); restore(); }
            int padding = mono ? 12 : 34; String face = mono ? MONO : REGULAR; float y = getY() + (height - 12) / 2f;
            save(); clip(getX() + padding, getY() + 2, width - padding - 9, height - 4);
            String value = getText(); int cursor = Math.clamp(getCursor(), 0, value.length());
            float caret = VectorCanvas.width(value.substring(0, cursor), 12, face);
            float scroll = Math.max(0, caret - (width - padding - 12)); float tx = getX() + padding - scroll;
            if (isFocused() && !getSelectedText().isEmpty()) {
                int end = Math.clamp(selection, 0, value.length()), start = Math.min(cursor, end), finish = Math.max(cursor, end);
                float startX = VectorCanvas.width(value.substring(0, start), 12, face), endX = VectorCanvas.width(value.substring(0, finish), 12, face);
                rect(tx + startX, y - 2, endX - startX, 16, 2, 0x804FA3D1);
            }
            text(value.isEmpty() && !isFocused() ? placeholder : value, tx, y, 12, value.isEmpty() ? DIM : TEXT, face);
            if (isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) rect(tx + caret, y - 1, 1, 15, 0, TEXT);
            restore();
        }
    }
}
