package dev.lyricair.visualcheck;

import dev.lyricair.LyricAirClient;
import dev.lyricair.config.LyricAirConfig;
import dev.lyricair.ui.LyricAirScreen;
import dev.lyricair.ui.OverlayEditorScreen;
import dev.lyricair.ui.VectorCanvas;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

public final class VisualCheckClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        AtomicInteger captured = new AtomicInteger();
        int[] ticks = {0};
        boolean[] started = {false};
        int[] warmup = {0};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null || client.getNetworkHandler() == null) return;
            client.mouse.unlockCursor();
            client.player.setYaw(ticks[0] >= 116 ? 90 : 0);
            client.player.setPitch(ticks[0] >= 116 ? 20 : 0);
            if (++warmup[0] < 20) return;
            if (!started[0]) {
                client.options.hudHidden = false;
                try {
                    ClientCommandManager.getActiveDispatcher().execute("lyricair demo",
                            (FabricClientCommandSource) client.getNetworkHandler().getCommandSource());
                    started[0] = true;
                    client.inGameHud.getChatHud().clear(false);
                    var coverCheck = client.runDirectory.toPath().toAbsolutePath().normalize().getParent().resolve(".tools/cover-pipeline-check.png");
                    if (Files.exists(coverCheck)) {
                        VectorCanvas.begin(new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers()));
                        int image = 0;
                        try {
                            image = VectorCanvas.image(Files.readAllBytes(coverCheck));
                            require(image > 0, "Vector thumbnail decode and GPU upload");
                            VectorCanvas.image(image, 0, 0, 32, 32, 5);
                        } finally { VectorCanvas.end(); VectorCanvas.deleteImage(image); }
                        LyricAirClient.LOGGER.info("Visual integration vector thumbnail decode, GPU upload and disposal passed");
                    }
                    LyricAirClient.LOGGER.info("Visual integration fixture started floating-lyrics demo");
                } catch (Exception error) {
                    LyricAirClient.LOGGER.error("Could not start visual integration fixture", error);
                    client.scheduleStop();
                    return;
                }
            }
            switch (++ticks[0]) {
                case 55 -> capture(client, "world", captured);
                case 60 -> command(client, "lyricair");
                case 67 -> capture(client, "display", captured);
                case 69 -> click(client.currentScreen, 450, 232);
                case 72 -> { click(client.currentScreen, 30, 138); click(client.currentScreen, 480, 146); require(config().followPlayer, "Follow toggle"); }
                case 77 -> capture(client, "world-settings", captured);
                case 79 -> click(client.currentScreen, 30, 226);
                case 81 -> {
                    click(client.currentScreen, 185, 157);
                    require(config().color == 0x67C8FF, "Color preset");
                    click(client.currentScreen, 370, 221);
                    TextFieldWidget field = (TextFieldWidget) client.currentScreen.children().stream().filter(child -> child instanceof TextFieldWidget textField && textField.getText().startsWith("#")).findFirst().orElseThrow();
                    field.setSelectionStart(0); field.setSelectionEnd(field.getText().length());
                    for (char character : "#A68CFF".toCharArray()) client.currentScreen.charTyped(character, 0);
                    require(config().color == 0xA68CFF, "Custom hex color");
                }
                case 87 -> capture(client, "color", captured);
                case 89 -> click(client.currentScreen, 30, 182);
                case 94 -> capture(client, "overlay-settings", captured);
                case 97 -> click(client.currentScreen, 640, 345);
                case 103 -> {
                    require(client.currentScreen instanceof OverlayEditorScreen, "Overlay editor opened");
                    client.currentScreen.mouseClicked(30, 65, 0);
                    client.currentScreen.mouseDragged(185, 170, 0, 155, 105);
                    client.currentScreen.mouseReleased(185, 170, 0);
                    require(config().overlayAnchor == LyricAirConfig.OverlayAnchor.CUSTOM, "Dragging saved custom position");
                }
                case 108 -> capture(client, "overlay-editor", captured);
                case 111, 114 -> client.currentScreen.close();
                case 116 -> client.player.setPosition(client.player.getX() + 20, client.player.getY(), client.player.getZ());
                case 130 -> capture(client, "both-follow", captured);
                case 133 -> command(client, "lyricair");
                case 136 -> { click(client.currentScreen, 30, 94); click(client.currentScreen, 300, 232); require(config().displayMode == LyricAirConfig.DisplayMode.OVERLAY, "Overlay-only mode"); }
                case 140 -> client.currentScreen.close();
                case 148 -> capture(client, "overlay", captured);
                case 155 -> command(client, "lyricair");
                case 158 -> {
                    click(client.currentScreen, 630, 27);
                    for (char character : "particles".toCharArray()) client.currentScreen.charTyped(character, 0);
                }
                case 162 -> capture(client, "search", captured);
                case 165 -> {
                    click(client.currentScreen, 250, 144);
                    click(client.currentScreen, 480, 250);
                    require(!config().particles, "Search result navigates to working setting");
                }
                case 168 -> {
                    double before = config().distance;
                    drag(client.currentScreen, 420, 302, 490, 302);
                    require(config().distance > before, "Inline slider drag");
                    client.currentScreen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
                    require(config().distance < 2.5 + (490 - 366) / 130.0 * 9.5, "Slider keyboard adjustment");
                }
                case 171 -> {
                    Screen menu = client.currentScreen;
                    float scale = panelScale(menu);
                    int px = (int) ((menu.width - LyricAirScreen.PANEL_WIDTH * scale) / 2 + 30 * scale);
                    int py = (int) ((menu.height - LyricAirScreen.PANEL_HEIGHT * scale) / 2 + 138 * scale);
                    var context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
                    for (int i = 0; i < 36; i++) {
                        menu.render(context, px, py, 0);
                        try { Thread.sleep(10); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                    }
                    capture(client, "expanded-sidebar", captured);
                }
            }
            if (ticks[0] >= 177 && captured.get() == 10) {
                LyricAirClient.LOGGER.info("VISUAL CHECK PASSED: vector fonts/artwork, modes, follow toggle, preset/hex, overlay drag, search, slider drag/keyboard and ten captures");
                client.scheduleStop();
            }
            if (ticks[0] > 400) { require(captured.get() == 10, "All ten screenshots completed"); client.scheduleStop(); }
        });
    }
    private static void command(MinecraftClient client, String command) {
        try { ClientCommandManager.getActiveDispatcher().execute(command, (FabricClientCommandSource) client.getNetworkHandler().getCommandSource()); }
        catch (Exception e) { throw new IllegalStateException("Visual fixture command failed", e); }
    }
    private static void capture(MinecraftClient client, String name, AtomicInteger captured) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "lyricair-" + name + ".png", client.getFramebuffer(), message -> {
            LyricAirClient.LOGGER.info("Visual integration screenshot: {}", message.getString()); captured.incrementAndGet();
        });
    }
    private static LyricAirConfig config() { return LyricAirConfig.load(FabricLoader.getInstance().getConfigDir().resolve("lyricair.json")); }
    private static void require(boolean value, String label) { if (!value) throw new IllegalStateException("Visual check failed: " + label); }
    private static void click(Screen screen, int x, int y) {
        require(screen instanceof LyricAirScreen, "Settings opened");
        float scale = panelScale(screen);
        double px = (int) ((screen.width - LyricAirScreen.PANEL_WIDTH * scale) / 2) + x * scale;
        double py = (int) ((screen.height - LyricAirScreen.PANEL_HEIGHT * scale) / 2) + y * scale;
        screen.mouseClicked(px, py, 0); screen.mouseReleased(px, py, 0);
    }
    private static float panelScale(Screen screen) { return Math.min(1, Math.min((screen.width - 16f) / LyricAirScreen.PANEL_WIDTH, (screen.height - 16f) / LyricAirScreen.PANEL_HEIGHT)); }
    private static void drag(Screen screen, int x, int y, int targetX, int targetY) {
        float scale = panelScale(screen);
        double left = (int) ((screen.width - LyricAirScreen.PANEL_WIDTH * scale) / 2), top = (int) ((screen.height - LyricAirScreen.PANEL_HEIGHT * scale) / 2);
        screen.mouseClicked(left + x * scale, top + y * scale, 0);
        screen.mouseDragged(left + targetX * scale, top + targetY * scale, 0, (targetX - x) * scale, (targetY - y) * scale);
        screen.mouseReleased(left + targetX * scale, top + targetY * scale, 0);
    }
}
