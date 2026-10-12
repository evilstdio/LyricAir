package dev.lyricair.ui;

import dev.lyricair.config.LyricAirConfig;
import dev.lyricair.render.LyricHudRenderer;
import dev.lyricair.render.OverlayLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OverlayEditorScreen extends Screen {
    private final Screen parent;
    private final LyricAirConfig config;
    private final LyricHudRenderer hud;
    private final Runnable save;
    private OverlayLayout layout;
    private boolean dragging;
    private double grabX, grabY;
    public OverlayEditorScreen(Screen parent, LyricAirConfig config, LyricHudRenderer hud, Runnable save) {
        super(Component.literal("Move LyricAir overlay")); this.parent = parent; this.config = config; this.hud = hud; this.save = save;
    }
    @Override protected void init() {
        addRenderableWidget(new Button(width / 2 - 35, height - 30, 70, 22, UiDraw.text("Done"), button -> onClose(), narration -> narration.get()) {
            @Override public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
                VectorCanvas.rect(getX(), getY(), getWidth(), getHeight(), 7, isHovered() ? 0xFF414B58 : 0xFF36404B);
                VectorCanvas.border(getX(), getY(), getWidth(), getHeight(), 7, 0x99D32F2F);
                VectorCanvas.center("Done", getX() + getWidth() / 2f, getY() + 5, 12, 0xFFF5F7FA);
            }
        });
    }
    @Override public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}
    @Override public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x55060D14);
        var content = hud.content(true); hud.drawAtLayout(context, content, true); layout = hud.layout(context, content);
        VectorCanvas.begin(context);
        try {
            VectorCanvas.center("Drag the card to place your overlay", width / 2f, 8, 12, 0xFFF5F7FA);
            VectorCanvas.center("Position adapts to your window · Esc to finish", width / 2f, 25, 10, 0xFFC3CBD5);
            super.render(context, mouseX, mouseY, delta);
        } finally { VectorCanvas.end(); }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && layout != null && layout.contains(x, y)) { dragging = true; grabX = x - layout.x(); grabY = y - layout.y(); return true; }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && layout != null && button == 0) {
            config.overlayAnchor = LyricAirConfig.OverlayAnchor.CUSTOM;
            config.overlayX = OverlayLayout.normalized(x - grabX, width, layout.width()); config.overlayY = OverlayLayout.normalized(y - grabY, height, layout.height()); return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) { dragging = false; save.run(); return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { save.run(); if (minecraft != null) minecraft.setScreen(parent); }
}
