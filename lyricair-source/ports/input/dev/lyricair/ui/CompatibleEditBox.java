package dev.lyricair.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class CompatibleEditBox extends EditBox {
    protected CompatibleEditBox(Font font, int x, int y, int width, int height, Component title) { super(font, x, y, width, height, title); }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { onClick(event.x(), event.y()); }
    public void onClick(double x, double y) {}
}
