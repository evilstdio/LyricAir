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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class VisualCheckClient implements ClientModInitializer {
    public static int hoverX = -1, hoverY = -1;
    @Override public void onInitializeClient() {
        AtomicInteger captured = new AtomicInteger();
        int[] ticks = {0};
        boolean[] started = {false};
        int[] warmup = {0};
        int[] bootTicks = {0};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null || client.getConnection() == null) {
                if (++bootTicks[0] % 100 == 0) LyricAirClient.LOGGER.info("Visual fixture waiting for world; screen={}", client.screen == null ? "none" : client.screen.getClass().getName());
                if (bootTicks[0] > 600) { client.stop(); throw new IllegalStateException("Visual fixture world did not load"); }
                return;
            }
            client.mouseHandler.releaseMouse();
            client.player.setYRot(ticks[0] >= 116 ? 90 : 0);
            client.player.setXRot(ticks[0] >= 116 ? 20 : 0);
            if (++warmup[0] < 20) return;
            if (!started[0]) {
                client.options.hideGui = false;
                try {
                    ClientCommandManager.getActiveDispatcher().execute("lyricair demo",
                            (FabricClientCommandSource) client.getConnection().getSuggestionsProvider());
                    started[0] = true;
                    client.gui.getChat().clearMessages(false);
                    var coverCheck = client.gameDirectory.toPath().toAbsolutePath().normalize().getParent().resolve(".tools/cover-pipeline-check.png");
                    if (Files.exists(coverCheck)) {
                        VectorCanvas.begin(new GuiGraphics(client, client.renderBuffers().bufferSource()));
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
                    client.stop();
                    return;
                }
            }
            switch (++ticks[0]) {
                case 55 -> capture(client, "world", captured);
                case 60 -> command(client, "lyricair");
                case 67 -> capture(client, "display", captured);
                case 69 -> click(client.screen, 450, 232);
                case 72 -> { click(client.screen, 30, 138); click(client.screen, 480, 146); require(config().followPlayer, "Follow toggle"); }
                case 77 -> capture(client, "world-settings", captured);
                case 79 -> click(client.screen, 30, 226);
                case 81 -> {
                    click(client.screen, 185, 157);
                    require(config().color == 0x67C8FF, "Color preset");
                    click(client.screen, 370, 221);
                    EditBox field = (EditBox) client.screen.children().stream().filter(child -> child instanceof EditBox textField && textField.getValue().startsWith("#")).findFirst().orElseThrow();
                    field.setCursorPosition(0); field.setHighlightPos(field.getValue().length());
                    for (char character : "#A68CFF".toCharArray()) client.screen.charTyped(character, 0);
                    require(config().color == 0xA68CFF, "Custom hex color");
                }
                case 87 -> capture(client, "color", captured);
                case 89 -> click(client.screen, 30, 182);
                case 94 -> capture(client, "overlay-settings", captured);
                case 97 -> click(client.screen, 640, 345);
                case 103 -> {
                    require(client.screen instanceof OverlayEditorScreen, "Overlay editor opened");
                    client.screen.mouseClicked(30, 65, 0);
                    client.screen.mouseDragged(185, 170, 0, 155, 105);
                    client.screen.mouseReleased(185, 170, 0);
                    require(config().overlayAnchor == LyricAirConfig.OverlayAnchor.CUSTOM, "Dragging saved custom position");
                }
                case 108 -> capture(client, "overlay-editor", captured);
                case 111, 114 -> client.screen.onClose();
                case 116 -> client.player.setPos(client.player.getX() + 20, client.player.getY(), client.player.getZ());
                case 130 -> capture(client, "both-follow", captured);
                case 133 -> command(client, "lyricair");
                case 136 -> { click(client.screen, 30, 94); click(client.screen, 300, 232); require(config().displayMode == LyricAirConfig.DisplayMode.OVERLAY, "Overlay-only mode"); }
                case 140 -> client.screen.onClose();
                case 148 -> capture(client, "overlay", captured);
                case 155 -> command(client, "lyricair");
                case 158 -> {
                    click(client.screen, 630, 27);
                    for (char character : "particles".toCharArray()) client.screen.charTyped(character, 0);
                }
                case 162 -> capture(client, "search", captured);
                case 165 -> {
                    click(client.screen, 250, 144);
                    click(client.screen, 480, 250);
                    require(!config().particles, "Search result navigates to working setting");
                }
                case 168 -> {
                    double before = config().distance;
                    drag(client.screen, 420, 302, 490, 302);
                    require(config().distance > before, "Inline slider drag");
                    client.screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
                    require(config().distance < 2.5 + (490 - 366) / 130.0 * 9.5, "Slider keyboard adjustment");
                }
                case 171 -> {
                    Screen menu = client.screen;
                    float scale = panelScale(menu);
                    int px = (int) ((menu.width - LyricAirScreen.PANEL_WIDTH * scale) / 2 + 30 * scale);
                    int py = (int) ((menu.height - LyricAirScreen.PANEL_HEIGHT * scale) / 2 + 138 * scale);
                    hoverX = px; hoverY = py;
                }
                case 180 -> capture(client, "expanded-sidebar", captured);
            }
            if (ticks[0] >= 185 && captured.get() == 10) {
                LyricAirClient.LOGGER.info("VISUAL CHECK PASSED: vector fonts/artwork, modes, follow toggle, preset/hex, overlay drag, search, slider drag/keyboard and ten captures");
                client.stop();
            }
            if (ticks[0] > 400) { require(captured.get() == 10, "All ten screenshots completed"); client.stop(); }
        });
    }
    private static void command(Minecraft client, String command) {
        try { ClientCommandManager.getActiveDispatcher().execute(command, (FabricClientCommandSource) client.getConnection().getSuggestionsProvider()); }
        catch (Exception e) { throw new IllegalStateException("Visual fixture command failed", e); }
    }
    private static void capture(Minecraft client, String name, AtomicInteger captured) {
        Screenshot.grab(client.gameDirectory, "lyricair-" + name + ".png", client.getMainRenderTarget(), message -> {
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
