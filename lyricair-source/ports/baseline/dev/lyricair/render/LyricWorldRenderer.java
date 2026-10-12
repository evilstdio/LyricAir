package dev.lyricair.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lyricair.LyricController;
import dev.lyricair.config.LyricAirConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

public final class LyricWorldRenderer implements AutoCloseable {
    private final LyricController controller;
    private final LyricAirConfig config;
    private final ByteBufferBuilder allocator = new ByteBufferBuilder(65_536);
    private final MultiBufferSource.BufferSource consumers = MultiBufferSource.immediate(allocator);

    public LyricWorldRenderer(LyricController controller, LyricAirConfig config) {
        this.controller = controller;
        this.config = config;
    }

    public void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!config.enabled || !config.displayMode.world() || client.options.hideGui || controller.bubbles().isEmpty()) return;
        double time = controller.visualTime();
        if (controller.advancing()) time += context.tickCounter().getGameTimeDeltaPartialTick(false) / 20.0;
        // LAST supplies the camera view through RenderSystem. Keep our model stack and
        // buffers independent of vanilla's layer-specific entity flushes.
        PoseStack matrices = new PoseStack();
        Vec3 camera = context.camera().getPosition();
        for (FloatingLyric bubble : controller.bubbles()) {
            float alpha = bubble.opacity(time);
            // Minecraft treats alpha <= 3 as opaque. Skip those samples entirely.
            int opacity = Math.round(alpha * 255);
            if (opacity <= 3) continue;
            Vec3 position = (config.followPlayer ? bubble.position(time,
                    FollowAnchor.origin(camera, context.camera().getYRot(), context.camera().getXRot(), config.distance, bubble.side),
                    FollowAnchor.right(context.camera().getYRot()), FollowAnchor.forward(context.camera().getYRot(), context.camera().getXRot()))
                    : bubble.position(time)).subtract(camera);
            matrices.pushPose();
            float scale = (float) config.textScale * bubble.scale(time);
            float roll = (float) Math.toRadians(Math.sin(time * 0.6 + bubble.seed) * 2.0);
            matrices.mulPose(BillboardTransform.matrix((float) position.x, (float) position.y,
                    (float) position.z, context.camera().rotation(), roll, scale));
            float top = -(bubble.rows.size() * 12 - 3) / 2f;
            // Layered geometry forms a short extrusion behind the luminous face of every glyph.
            for (int layer = 5; layer >= 1; layer--) {
                matrices.pushPose();
                matrices.translate(layer * 0.19, layer * 0.19, -layer * 0.65);
                int depthColor = (opacity << 24) | shade(config.color, 0.20 + (5 - layer) * 0.055);
                drawRows(client.font, bubble, top, depthColor, matrices, context);
                matrices.popPose();
            }
            drawRows(client.font, bubble, top, (opacity << 24) | config.color, matrices, context);
            matrices.popPose();
        }
        consumers.endBatch();
    }

    private void drawRows(Font renderer, FloatingLyric bubble, float top, int color,
                          PoseStack matrices, WorldRenderContext context) {
        for (int row = 0; row < bubble.rows.size(); row++) {
            FormattedCharSequence text = bubble.rows.get(row);
            renderer.drawInBatch(text, -renderer.width(text) / 2f, top + row * 12, color, false,
                    matrices.last().pose(), consumers, Font.DisplayMode.NORMAL,
                    0, LightTexture.FULL_BRIGHT);
        }
    }

    private static int shade(int color, double factor) {
        return ((int) (((color >> 16) & 255) * factor) << 16)
                | ((int) (((color >> 8) & 255) * factor) << 8) | (int) ((color & 255) * factor);
    }
    @Override public void close() { allocator.close(); }
}

