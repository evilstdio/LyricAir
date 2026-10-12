package dev.lyricair.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Small version adapter: uploads only the painted bounds, then submits an ordinary GUI texture. */
public final class CanvasTexture implements AutoCloseable {
    private final ResourceLocation id;
    private DynamicTexture texture;
    private NativeImage image;
    private int[] currentPixels, uploadedPixels;
    public CanvasTexture(String name) { id = ResourceLocation.fromNamespaceAndPath("lyricair", name); }
    public void draw(GuiGraphics gui, BufferedImage pixels, int x, int y, int width, int height, double ratio) {
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            close(); image = new NativeImage(width, height, false); texture = new DynamicTexture(image);
            texture.setFilter(true, false); Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        int count = width * height;
        if (currentPixels == null || currentPixels.length != count) currentPixels = new int[count];
        pixels.getRGB(x, y, width, height, currentPixels, 0, width);
        if (!Arrays.equals(currentPixels, uploadedPixels)) {
            for (int row = 0; row < height; row++) for (int col = 0; col < width; col++) image.setPixel(col, row, currentPixels[row * width + col]);
            texture.upload();
            if (uploadedPixels == null || uploadedPixels.length != count) uploadedPixels = new int[count];
            System.arraycopy(currentPixels, 0, uploadedPixels, 0, count);
        }
        int gx = (int) Math.floor(x / ratio), gy = (int) Math.floor(y / ratio);
        int gw = (int) Math.ceil((x + width) / ratio) - gx, gh = (int) Math.ceil((y + height) / ratio) - gy;
        gui.blit(RenderType::guiTextured, id, gx, gy, 0, 0, gw, gh, width, height, width, height);
    }
    @Override public void close() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
        texture = null; image = null;
        uploadedPixels = null;
    }
}
