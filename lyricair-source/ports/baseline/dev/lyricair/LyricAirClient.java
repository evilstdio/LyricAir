package dev.lyricair;

import dev.lyricair.config.LyricAirConfig;
import dev.lyricair.render.LyricWorldRenderer;
import dev.lyricair.render.LyricHudRenderer;
import dev.lyricair.ui.LyricAirScreen;
import dev.lyricair.ui.VectorCanvas;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.blaze3d.platform.InputConstants;

public final class LyricAirClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lyricair");
    private LyricAirConfig config;
    private Path configPath;
    private LyricController controller;
    private LyricHudRenderer hud;

    @Override public void onInitializeClient() {
        Minecraft client = Minecraft.getInstance();
        Path configDirectory = FabricLoader.getInstance().getConfigDir();
        configPath = configDirectory.resolve("lyricair.json");
        config = LyricAirConfig.load(configPath);
        config.save(configPath);
        controller = new LyricController(client, config, configDirectory.resolve("lyricair"));
        hud = new LyricHudRenderer(controller, config);
        KeyMapping settings = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricair.settings",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.lyricair"));
        KeyMapping toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricair.toggle",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.lyricair"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (settings.consumeClick()) mc.setScreen(new LyricAirScreen(mc.screen, config, controller, hud, this::save));
            while (toggle.consumeClick()) toggle(mc);
            controller.tick();
        });
        LyricWorldRenderer renderer = new LyricWorldRenderer(controller, config);
        WorldRenderEvents.LAST.register(renderer::render);
        HudRenderCallback.EVENT.register(hud::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> { controller.close(); renderer.close(); hud.close(); VectorCanvas.destroy(); });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("lyricair")
                .executes(context -> { openSettings(); return 1; })
                .then(literal("demo").executes(context -> {
                    controller.toggleDemo(); save();
                    context.getSource().sendFeedback(Component.literal(controller.demo() ? "LyricAir demo started. Look above the horizon." : "LyricAir demo stopped."));
                    return 1;
                }))
                .then(literal("toggle").executes(context -> { toggle(client); return 1; }))
                .then(literal("reconnect").executes(context -> { controller.reconnect(); return 1; }))
                .then(literal("status").executes(context -> { context.getSource().sendFeedback(Component.literal(controller.status())); return 1; }))));
        LOGGER.info("LyricAir ready: O for settings, K to toggle, /lyricair demo to preview floating lyrics");
    }

    private void toggle(Minecraft client) {
        config.enabled = !config.enabled;
        controller.clearVisuals();
        save();
        if (client.player != null) client.player.displayClientMessage(Component.literal("LyricAir " + (config.enabled ? "enabled" : "disabled")), true);
    }
    private void openSettings() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreen(new LyricAirScreen(client.screen, config, controller, hud, this::save)));
    }
    private void save() { config.save(configPath); }
}
