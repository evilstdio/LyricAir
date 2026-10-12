package dev.lyricair.ui;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;
import static org.lwjgl.opengl.GL33.*;

/** Smooth, resolution-independent menu drawing. Owns its native fonts and restores all GL state it touches. */
public final class VectorCanvas {
    public static final String REGULAR = "Inter", BOLD = "Inter-Bold", MONO = "Inter-Mono";
    private static final List<ByteBuffer> FONTS = new ArrayList<>();
    private static long vg;
    private static GlSnapshot snapshot;
    private VectorCanvas() {}

    public static void begin(GuiGraphics context) {
        context.flush();
        snapshot = new GlSnapshot();
        try {
            if (vg == 0) {
                vg = nvgCreate(NVG_ANTIALIAS);
                if (vg == 0) throw new IllegalStateException("Could not create LyricAir's vector renderer");
                load(REGULAR, "inter-regular.otf"); load(BOLD, "inter-bold.otf"); load(MONO, "inter-mono.otf");
            }
            var client = Minecraft.getInstance();
            var target = client.getMainRenderTarget();
            glBindFramebuffer(GL_FRAMEBUFFER, target.frameBufferId);
            glViewport(0, 0, target.width, target.height);
            glActiveTexture(GL_TEXTURE0); glBindSampler(0, 0);
            glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST);
            nvgBeginFrame(vg, context.guiWidth(), context.guiHeight(),
                    target.width / (float) context.guiWidth());
        } catch (RuntimeException | Error error) {
            snapshot.restore(); snapshot = null;
            destroy();
            throw error;
        }
    }
    private static void load(String name, String file) {
        try (var stream = VectorCanvas.class.getResourceAsStream("/assets/lyricair/fonts/" + file)) {
            if (stream == null) throw new IOException("Missing font " + file);
            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length).put(bytes).flip(); FONTS.add(buffer);
            if (nvgCreateFontMem(vg, name, buffer, false) < 0) throw new IOException("Invalid font " + file);
        } catch (IOException error) { throw new IllegalStateException("Could not load menu font", error); }
    }
    public static void end() {
        try { nvgEndFrame(vg); }
        finally { snapshot.restore(); snapshot = null; }
    }
    public static void destroy() {
        if (vg != 0) { nvgDelete(vg); vg = 0; }
        FONTS.forEach(MemoryUtil::memFree); FONTS.clear();
    }
    public static boolean ready() { return vg != 0; }
    public static int image(byte[] png) {
        ByteBuffer data = MemoryUtil.memAlloc(png.length).put(png).flip();
        try { return nvgCreateImageMem(vg, 0, data); }
        finally { MemoryUtil.memFree(data); }
    }
    public static void deleteImage(int image) { if (image > 0 && vg != 0) nvgDeleteImage(vg, image); }
    public static void image(int image, float x, float y, float width, float height, float radius) {
        try (var stack = MemoryStack.stackPush()) {
            NVGPaint paint = NVGPaint.malloc(stack);
            nvgImagePattern(vg, x, y, width, height, 0, image, 1, paint);
            nvgBeginPath(vg); nvgRoundedRect(vg, x, y, width, height, radius); nvgFillPaint(vg, paint); nvgFill(vg);
        }
    }
    public static void save() { nvgSave(vg); }
    public static void restore() { nvgRestore(vg); }
    public static void translate(float x, float y) { nvgTranslate(vg, x, y); }
    public static void scale(float scale) { nvgScale(vg, scale, scale); }
    public static void clip(float x, float y, float width, float height) { nvgIntersectScissor(vg, x, y, width, height); }
    private static NVGColor color(int argb, MemoryStack stack) {
        return NVGColor.malloc(stack).r(((argb >> 16) & 255) / 255f).g(((argb >> 8) & 255) / 255f)
                .b((argb & 255) / 255f).a(((argb >>> 24) & 255) / 255f);
    }
    public static void rect(float x, float y, float width, float height, float radius, int fill) {
        if (width <= 0 || height <= 0) return;
        try (var stack = MemoryStack.stackPush()) {
            nvgBeginPath(vg); nvgRoundedRect(vg, x, y, width, height, radius);
            nvgFillColor(vg, color(fill, stack)); nvgFill(vg);
        }
    }
    public static void border(float x, float y, float width, float height, float radius, int stroke) {
        try (var stack = MemoryStack.stackPush()) {
            nvgBeginPath(vg); nvgRoundedRect(vg, x + .5f, y + .5f, width - 1, height - 1, radius);
            nvgStrokeWidth(vg, 1); nvgStrokeColor(vg, color(stroke, stack)); nvgStroke(vg);
        }
    }
    public static void circle(float x, float y, float radius, int fill) {
        try (var stack = MemoryStack.stackPush()) {
            nvgBeginPath(vg); nvgCircle(vg, x, y, radius); nvgFillColor(vg, color(fill, stack)); nvgFill(vg);
        }
    }
    public static void line(float x1, float y1, float x2, float y2, float thickness, int stroke) {
        try (var stack = MemoryStack.stackPush()) {
            nvgBeginPath(vg); nvgMoveTo(vg, x1, y1); nvgLineTo(vg, x2, y2);
            nvgStrokeWidth(vg, thickness); nvgLineCap(vg, NVG_ROUND); nvgStrokeColor(vg, color(stroke, stack)); nvgStroke(vg);
        }
    }
    private static void font(String face, float size) {
        nvgFontFace(vg, face); nvgFontSize(vg, size); nvgTextLetterSpacing(vg, 0);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
    }
    public static void text(String text, float x, float y, float size, int fill) { text(text, x, y, size, fill, REGULAR); }
    public static void text(String text, float x, float y, float size, int fill, String face) {
        if (text.isEmpty()) return;
        try (var stack = MemoryStack.stackPush()) {
            font(face, size); nvgFillColor(vg, color(fill, stack)); nvgText(vg, x, y, text);
        }
    }
    public static float width(String text, float size) { return width(text, size, REGULAR); }
    public static float width(String text, float size, String face) {
        font(face, size); return nvgTextBounds(vg, 0, 0, text, (float[]) null);
    }
    public static void center(String text, float x, float y, float size, int fill) { text(text, x - width(text, size) / 2, y, size, fill); }
    public static String ellipsis(String text, float limit, float size) {
        if (width(text, size) <= limit) return text;
        int end = text.length();
        while (end > 0 && width(text.substring(0, end) + "…", size) > limit) end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + "…";
    }
    /** Original line icons; no textures or font glyphs are required. */
    public static void icon(String name, float x, float y, int color) {
        save(); translate(x, y);
        switch (name) {
            case "music" -> {
                line(8, 5, 8, 17, 1.7f, color); line(18, 3, 18, 15, 1.7f, color); line(8, 5, 18, 3, 1.7f, color);
                circle(5.5f, 17, 3, color); circle(15.5f, 15, 3, color);
            }
            case "display" -> { border(2, 3, 20, 14, 3, color); line(12, 17, 12, 21, 1.5f, color); line(7, 21, 17, 21, 1.5f, color); }
            case "world" -> {
                line(12, 2, 22, 7, 1.5f, color); line(22, 7, 12, 12, 1.5f, color); line(12, 12, 2, 7, 1.5f, color); line(2, 7, 12, 2, 1.5f, color);
                line(2, 7, 2, 17, 1.5f, color); line(22, 7, 22, 17, 1.5f, color); line(12, 12, 12, 22, 1.5f, color);
                line(2, 17, 12, 22, 1.5f, color); line(12, 22, 22, 17, 1.5f, color);
            }
            case "overlay" -> { border(2, 3, 20, 18, 3, color); line(2, 9, 22, 9, 1, color); line(6, 13, 15, 13, 1.5f, color); line(6, 17, 18, 17, 1.5f, color); }
            case "color" -> { border(2, 2, 20, 20, 10, color); circle(8, 8, 1.8f, color); circle(15, 7, 1.8f, color); circle(18, 13, 1.8f, color); circle(8, 16, 1.8f, color); }
            case "search" -> { border(3, 3, 13, 13, 7, color); line(15, 15, 21, 21, 1.6f, color); }
            case "check" -> { line(5, 12, 10, 17, 1.8f, color); line(10, 17, 19, 7, 1.8f, color); }
            case "chevron" -> { line(7, 10, 12, 15, 1.5f, color); line(12, 15, 17, 10, 1.5f, color); }
        }
        restore();
    }

    private static final class GlSnapshot {
        final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING), buffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        final int drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE), texture, sampler;
        final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB), srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        final int eqRgb = glGetInteger(GL_BLEND_EQUATION_RGB), eqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        final boolean depth = glIsEnabled(GL_DEPTH_TEST), blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE), scissor = glIsEnabled(GL_SCISSOR_TEST), stencil = glIsEnabled(GL_STENCIL_TEST);
        final int cullMode = glGetInteger(GL_CULL_FACE_MODE), front = glGetInteger(GL_FRONT_FACE);
        final int[] viewport = new int[4], scissorBox = new int[4], stencilFront = stencil(GL_FRONT), stencilBack = stencil(GL_BACK);
        final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        final ByteBuffer colorMask = MemoryUtil.memAlloc(4);
        GlSnapshot() {
            glGetIntegerv(GL_VIEWPORT, viewport); glGetIntegerv(GL_SCISSOR_BOX, scissorBox); glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
            glActiveTexture(GL_TEXTURE0); texture = glGetInteger(GL_TEXTURE_BINDING_2D); sampler = glGetInteger(GL_SAMPLER_BINDING);
        }
        private static int[] stencil(int face) {
            boolean back = face == GL_BACK;
            return new int[]{glGetInteger(back ? GL_STENCIL_BACK_FUNC : GL_STENCIL_FUNC), glGetInteger(back ? GL_STENCIL_BACK_REF : GL_STENCIL_REF),
                    glGetInteger(back ? GL_STENCIL_BACK_VALUE_MASK : GL_STENCIL_VALUE_MASK), glGetInteger(back ? GL_STENCIL_BACK_WRITEMASK : GL_STENCIL_WRITEMASK),
                    glGetInteger(back ? GL_STENCIL_BACK_FAIL : GL_STENCIL_FAIL), glGetInteger(back ? GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL_STENCIL_PASS_DEPTH_FAIL),
                    glGetInteger(back ? GL_STENCIL_BACK_PASS_DEPTH_PASS : GL_STENCIL_PASS_DEPTH_PASS)};
        }
        private static void restoreStencil(int face, int[] state) { glStencilFuncSeparate(face, state[0], state[1], state[2]); glStencilMaskSeparate(face, state[3]); glStencilOpSeparate(face, state[4], state[5], state[6]); }
        void restore() {
            glUseProgram(program); glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, buffer);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo); glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo); glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture); glBindSampler(0, sampler); glActiveTexture(activeTexture);
            capability(GL_DEPTH_TEST, depth); capability(GL_BLEND, blend); capability(GL_CULL_FACE, cull); capability(GL_SCISSOR_TEST, scissor); capability(GL_STENCIL_TEST, stencil);
            glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha); glBlendEquationSeparate(eqRgb, eqAlpha); glCullFace(cullMode); glFrontFace(front);
            glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]); glDepthMask(depthMask);
            glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0); MemoryUtil.memFree(colorMask);
            restoreStencil(GL_FRONT, stencilFront); restoreStencil(GL_BACK, stencilBack);
        }
        private static void capability(int name, boolean enabled) { if (enabled) glEnable(name); else glDisable(name); }
    }
}
