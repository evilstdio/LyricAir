package dev.lyricair.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/** Preserves panel-local coordinates while bridging the engine's structured input events. */
public abstract class CompatibleScreen extends Screen {
    private static boolean shift;
    protected CompatibleScreen(Component title) { super(title); }
    public static boolean shiftDown() { return shift; }
    static MouseButtonEvent mouse(double x, double y, int button) { return new MouseButtonEvent(x, y, new MouseButtonInfo(button, shift ? com.mojang.blaze3d.platform.InputConstants.MOD_SHIFT : 0)); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { shift = event.hasShiftDown(); return mouseClicked(event.x(), event.y(), event.button()); }
    public boolean mouseClicked(double x, double y, int button) { return super.mouseClicked(mouse(x, y, button), false); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { shift = event.hasShiftDown(); return mouseReleased(event.x(), event.y(), event.button()); }
    public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(mouse(x, y, button)); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { shift = event.hasShiftDown(); return mouseDragged(event.x(), event.y(), event.button(), dx, dy); }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged(mouse(x, y, button), dx, dy); }
    @Override public boolean keyPressed(KeyEvent event) { shift = event.hasShiftDown(); return keyPressed(event.key(), event.scancode(), event.modifiers()); }
    public boolean keyPressed(int key, int scan, int modifiers) { return super.keyPressed(new KeyEvent(key, scan, modifiers)); }
    public boolean charTyped(char character, int modifiers) { return super.charTyped(new CharacterEvent(character, modifiers)); }
}
