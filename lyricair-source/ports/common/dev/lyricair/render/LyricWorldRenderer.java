package dev.lyricair.render;

import com.mojang.math.Transformation;
import dev.lyricair.LyricController;
import dev.lyricair.config.LyricAirConfig;
import dev.lyricair.mixin.DisplayAccess;
import dev.lyricair.mixin.TextDisplayAccess;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Client-local display geometry lets each Minecraft version handle its own rendering pipeline. */
public final class LyricWorldRenderer implements AutoCloseable {
    private final LyricController controller;
    private final LyricAirConfig config;
    private final Map<FloatingLyric, Display.TextDisplay[]> displays = new IdentityHashMap<>();
    private ClientLevel world;
    private static int nextId = -1_000_000;
    public LyricWorldRenderer(LyricController controller, LyricAirConfig config) { this.controller = controller; this.config = config; }
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != world) { close(); world = client.level; }
        if (world == null || !config.enabled || !config.displayMode.world() || client.options.hideGui) { close(); return; }
        displays.entrySet().removeIf(entry -> {
            if (controller.bubbles().contains(entry.getKey())) return false;
            remove(entry.getValue()); return true;
        });
        var camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.getPosition(); double time = controller.visualTime();
        for (var bubble : controller.bubbles()) {
            var layers = displays.computeIfAbsent(bubble, this::create);
            Vec3 position = config.followPlayer ? bubble.position(time,
                    FollowAnchor.origin(cameraPosition, camera.getYRot(), camera.getXRot(), config.distance, bubble.side),
                    FollowAnchor.right(camera.getYRot()), FollowAnchor.forward(camera.getYRot(), camera.getXRot())) : bubble.position(time);
            float scale = (float) (config.textScale / .025) * bubble.scale(time);
            int opacity = Math.round(bubble.opacity(time) * 255);
            float roll = (float) Math.toRadians(Math.sin(time * .6 + bubble.seed) * 2);
            for (int layer = 0; layer < layers.length; layer++) {
                var display = layers[layer]; display.setPos(position.x, position.y, position.z);
                float depth = -layer * .0065f;
                ((DisplayAccess) display).lyricair$transform(new Transformation(new Vector3f(layer * .00019f, layer * -.00019f, depth),
                        new Quaternionf().rotateZ(roll), new Vector3f(scale), new Quaternionf()));
                int color = layer == 0 ? config.color : shade(config.color, .20 + (5 - layer) * .055);
                var access = (TextDisplayAccess) display;
                access.lyricair$text(opacity <= 3 ? Component.empty() : Component.literal(plain(bubble)).withStyle(style -> style.withColor(color)));
                access.lyricair$opacity((byte) opacity);
            }
        }
    }
    private Display.TextDisplay[] create(FloatingLyric bubble) {
        var layers = new Display.TextDisplay[6];
        for (int i = 0; i < layers.length; i++) {
            var display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
            display.setId(nextId--);
            ((DisplayAccess) display).lyricair$billboard(Display.BillboardConstraints.CENTER);
            ((DisplayAccess) display).lyricair$brightness(new Brightness(15, 15));
            ((DisplayAccess) display).lyricair$interpolation(1);
            var access = (TextDisplayAccess) display;
            access.lyricair$background(0); access.lyricair$flags((byte) 0); access.lyricair$lineWidth(400);
            access.lyricair$text(Component.literal(plain(bubble)));
            Vec3 position = bubble.position(controller.visualTime()); display.setPos(position.x, position.y, position.z);
            world.addEntity(display); layers[i] = display;
        }
        return layers;
    }
    private static String plain(FloatingLyric bubble) {
        StringBuilder value = new StringBuilder();
        for (var row : bubble.rows) {
            if (!value.isEmpty()) value.append('\n');
            row.accept((index, style, codepoint) -> { value.appendCodePoint(codepoint); return true; });
        }
        return value.toString();
    }
    private static int shade(int color, double factor) { return ((int) (((color >> 16) & 255) * factor) << 16) | ((int) (((color >> 8) & 255) * factor) << 8) | (int) ((color & 255) * factor); }
    private static void remove(Display.TextDisplay[] displays) { for (var display : displays) display.remove(Entity.RemovalReason.DISCARDED); }
    @Override public void close() { displays.values().forEach(LyricWorldRenderer::remove); displays.clear(); }
}
