package dev.lyricair.render;

import dev.lyricair.LyricController;
import dev.lyricair.config.LyricAirConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.Vec3d;

public final class LyricWorldRenderer implements AutoCloseable {
    private final LyricController controller;
    private final LyricAirConfig config;
    private final BufferAllocator allocator = new BufferAllocator(65_536);
    private final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);

    public LyricWorldRenderer(LyricController controller, LyricAirConfig config) {
        this.controller = controller;
        this.config = config;
    }

    public void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.enabled || !config.displayMode.world() || client.options.hudHidden || controller.bubbles().isEmpty()) return;
        double time = controller.visualTime();
        if (controller.advancing()) time += context.tickCounter().getTickDelta(false) / 20.0;
        MatrixStack matrices = new MatrixStack();
        Vec3d camera = context.camera().getPos();
        for (FloatingLyric bubble : controller.bubbles()) {
            float alpha = bubble.opacity(time);
            int opacity = Math.round(alpha * 255);
            if (opacity <= 3) continue;
            Vec3d position = (config.followPlayer ? bubble.position(time,
                    FollowAnchor.origin(camera, context.camera().getYaw(), context.camera().getPitch(), config.distance, bubble.side),
                    FollowAnchor.right(context.camera().getYaw()), FollowAnchor.forward(context.camera().getYaw(), context.camera().getPitch()))
                    : bubble.position(time)).subtract(camera);
            matrices.push();
            float scale = (float) config.textScale * bubble.scale(time);
            float roll = (float) Math.toRadians(Math.sin(time * 0.6 + bubble.seed) * 2.0);
            matrices.multiplyPositionMatrix(BillboardTransform.matrix((float) position.x, (float) position.y,
                    (float) position.z, context.camera().getRotation(), roll, scale));
            float top = -(bubble.rows.size() * 12 - 3) / 2f;
            for (int layer = 5; layer >= 1; layer--) {
                matrices.push();
                matrices.translate(layer * 0.19, layer * 0.19, -layer * 0.65);
                int depthColor = (opacity << 24) | shade(config.color, 0.20 + (5 - layer) * 0.055);
                drawRows(client.textRenderer, bubble, top, depthColor, matrices, context);
                matrices.pop();
            }
            drawRows(client.textRenderer, bubble, top, (opacity << 24) | config.color, matrices, context);
            matrices.pop();
        }
        consumers.draw();
    }

    private void drawRows(TextRenderer renderer, FloatingLyric bubble, float top, int color,
                          MatrixStack matrices, WorldRenderContext context) {
        for (int row = 0; row < bubble.rows.size(); row++) {
            OrderedText text = bubble.rows.get(row);
            renderer.draw(text, -renderer.getWidth(text) / 2f, top + row * 12, color, false,
                    matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL,
                    0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }

    private static int shade(int color, double factor) {
        return ((int) (((color >> 16) & 255) * factor) << 16)
                | ((int) (((color >> 8) & 255) * factor) << 8) | (int) ((color & 255) * factor);
    }
    @Override public void close() { allocator.close(); }
}

