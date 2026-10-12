package dev.lyricair.ui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class CompatibleWidget extends AbstractWidget {
    protected CompatibleWidget(int x, int y, int width, int height, Component title) { super(x, y, width, height, title); }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { onClick(event.x(), event.y()); }
    public void onClick(double x, double y) {}
    @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { onDrag(event.x(), event.y(), dx, dy); }
    protected void onDrag(double x, double y, double dx, double dy) {}
    @Override public void onRelease(MouseButtonEvent event) { onRelease(event.x(), event.y()); }
    public void onRelease(double x, double y) {}
    @Override public boolean keyPressed(KeyEvent event) { return keyPressed(event.key(), event.scancode(), event.modifiers()); }
    public boolean keyPressed(int key, int scan, int modifiers) { return super.keyPressed(new KeyEvent(key, scan, modifiers)); }
}
