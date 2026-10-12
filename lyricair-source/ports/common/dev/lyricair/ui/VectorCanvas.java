package dev.lyricair.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Backend-independent smooth vector canvas; Minecraft owns the texture upload and composition. */
public final class VectorCanvas {
    public static final String REGULAR = "Inter", BOLD = "Inter-Bold", MONO = "Inter-Mono";
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private static final Map<String, Font> FONTS = new HashMap<>();
    private static final Map<String, CanvasTexture> SURFACES = new HashMap<>();
    private static final Map<Integer, BufferedImage> IMAGES = new HashMap<>();
    private static final ArrayDeque<State> STACK = new ArrayDeque<>();
    private static int nextImage = 1;
    private static BufferedImage pixels;
    private static Graphics2D graphics;
    private static GuiGraphics context;
    private static CanvasTexture surface;
    private static Rectangle2D bounds;
    private static double ratio;
    private record State(AffineTransform transform, Shape clip) {}
    private VectorCanvas() {}

    private static void fonts() {
        if (!FONTS.isEmpty()) return;
        for (var entry : Map.of(REGULAR, "inter-regular.otf", BOLD, "inter-bold.otf", MONO, "inter-mono.otf").entrySet()) {
            try (var stream = VectorCanvas.class.getResourceAsStream("/assets/lyricair/fonts/" + entry.getValue())) {
                if (stream == null) throw new IllegalStateException("Missing Inter font");
                FONTS.put(entry.getKey(), Font.createFont(Font.TRUETYPE_FONT, stream));
            } catch (Exception error) { throw new IllegalStateException("Could not load Inter font", error); }
        }
    }
    public static void begin(GuiGraphics target) {
        if (graphics != null) throw new IllegalStateException("Nested canvas frame");
        fonts(); context = target;
        String caller = StackWalker.getInstance().walk(frames -> frames.skip(1).findFirst().orElseThrow().getClassName());
        surface = SURFACES.computeIfAbsent(caller, name -> new CanvasTexture("canvas_" + SURFACES.size()));
        var window = Minecraft.getInstance().getWindow();
        ratio = window.getWidth() / (double) target.guiWidth();
        int w = Math.max(1, window.getWidth()), h = Math.max(1, window.getHeight());
        if (pixels == null || pixels.getWidth() != w || pixels.getHeight() != h) pixels = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        graphics = pixels.createGraphics(); graphics.setComposite(AlphaComposite.Clear); graphics.fillRect(0, 0, w, h);
        graphics.setComposite(AlphaComposite.SrcOver); graphics.scale(ratio, ratio);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        bounds = null; STACK.clear();
    }
    public static void end() {
        graphics.dispose(); graphics = null;
        if (bounds != null) {
            int x = Math.max(0, (int) Math.floor(bounds.getMinX()) - 2), y = Math.max(0, (int) Math.floor(bounds.getMinY()) - 2);
            int right = Math.min(pixels.getWidth(), (int) Math.ceil(bounds.getMaxX()) + 2), bottom = Math.min(pixels.getHeight(), (int) Math.ceil(bounds.getMaxY()) + 2);
            if (right > x && bottom > y) surface.draw(context, pixels, x, y, right - x, bottom - y, ratio);
        }
        context = null; surface = null;
    }
    public static void destroy() { SURFACES.values().forEach(CanvasTexture::close); SURFACES.clear(); FONTS.clear(); IMAGES.clear(); pixels = null; }
    public static boolean ready() { return !FONTS.isEmpty(); }
    public static void save() { STACK.push(new State(graphics.getTransform(), graphics.getClip())); }
    public static void restore() { State state = STACK.pop(); graphics.setTransform(state.transform); graphics.setClip(state.clip); }
    public static void translate(float x, float y) { graphics.translate(x, y); }
    public static void scale(float scale) { graphics.scale(scale, scale); }
    public static void clip(float x, float y, float w, float h) { graphics.clip(new Rectangle2D.Float(x, y, w, h)); }
    private static void track(Shape shape) {
        Rectangle2D rectangle = graphics.getTransform().createTransformedShape(shape).getBounds2D();
        if (bounds == null) bounds = rectangle; else bounds.add(rectangle);
    }
    private static void fill(Shape shape, int color) { graphics.setColor(new Color(color, true)); graphics.fill(shape); track(shape); }
    public static void rect(float x, float y, float w, float h, float radius, int color) {
        if (w > 0 && h > 0 && (color >>> 24) != 0) fill(new RoundRectangle2D.Float(x, y, w, h, radius * 2, radius * 2), color);
    }
    public static void border(float x, float y, float w, float h, float radius, int color) {
        Shape shape = new RoundRectangle2D.Float(x + .5f, y + .5f, w - 1, h - 1, radius * 2, radius * 2);
        graphics.setColor(new Color(color, true)); graphics.setStroke(new BasicStroke(1)); graphics.draw(shape); track(shape);
    }
    public static void circle(float x, float y, float radius, int color) { fill(new Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2), color); }
    public static void line(float x1, float y1, float x2, float y2, float width, int color) {
        Shape shape = new Line2D.Float(x1, y1, x2, y2); graphics.setColor(new Color(color, true));
        graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); graphics.draw(shape); track(new BasicStroke(width).createStrokedShape(shape));
    }
    private static Font font(String face, float size) { fonts(); return FONTS.get(face).deriveFont(size); }
    public static float width(String text, float size) { return width(text, size, REGULAR); }
    public static float width(String text, float size, String face) { return (float) font(face, size).getStringBounds(text, METRICS).getWidth(); }
    public static void text(String text, float x, float y, float size, int color) { text(text, x, y, size, color, REGULAR); }
    public static void text(String text, float x, float y, float size, int color, String face) {
        if (text.isEmpty()) return;
        Font font = font(face, size); graphics.setFont(font); graphics.setColor(new Color(color, true));
        float baseline = y + (float) font.getLineMetrics(text, METRICS).getAscent(); graphics.drawString(text, x, baseline);
        track(new Rectangle2D.Float(x, y, width(text, size, face) + 2, size * 1.4f));
    }
    public static void center(String text, float x, float y, float size, int color) { text(text, x - width(text, size) / 2, y, size, color); }
    public static String ellipsis(String text, float limit, float size) {
        if (width(text, size) <= limit) return text;
        int end = text.length(); while (end > 0 && width(text.substring(0, end) + "…", size) > limit) end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + "…";
    }
    public static int image(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null || image.getWidth() > 512 || image.getHeight() > 512) return 0;
            int id = nextImage++; IMAGES.put(id, image); return id;
        } catch (Exception ignored) { return 0; }
    }
    public static void deleteImage(int id) { IMAGES.remove(id); }
    public static void image(int id, float x, float y, float w, float h, float radius) {
        var image = IMAGES.get(id); if (image == null) return;
        save(); Shape shape = new RoundRectangle2D.Float(x, y, w, h, radius * 2, radius * 2); graphics.clip(shape);
        AffineTransform transform = AffineTransform.getTranslateInstance(x, y); transform.scale(w / image.getWidth(), h / image.getHeight()); graphics.drawImage(image, transform, null); track(shape); restore();
    }
    public static void icon(String name, float x, float y, int color) {
        save(); translate(x, y);
        switch (name) {
            case "music" -> { line(8, 5, 8, 17, 1.7f, color); line(18, 3, 18, 15, 1.7f, color); line(8, 5, 18, 3, 1.7f, color); circle(5.5f, 17, 3, color); circle(15.5f, 15, 3, color); }
            case "display" -> { border(2, 3, 20, 14, 3, color); line(12, 17, 12, 21, 1.5f, color); line(7, 21, 17, 21, 1.5f, color); }
            case "world" -> {
                line(12, 2, 22, 7, 1.5f, color); line(22, 7, 12, 12, 1.5f, color); line(12, 12, 2, 7, 1.5f, color); line(2, 7, 12, 2, 1.5f, color);
                line(2, 7, 2, 17, 1.5f, color); line(22, 7, 22, 17, 1.5f, color); line(12, 12, 12, 22, 1.5f, color); line(2, 17, 12, 22, 1.5f, color); line(12, 22, 22, 17, 1.5f, color);
            }
            case "overlay" -> { border(2, 3, 20, 18, 3, color); line(2, 9, 22, 9, 1, color); line(6, 13, 15, 13, 1.5f, color); line(6, 17, 18, 17, 1.5f, color); }
            case "color" -> { border(2, 2, 20, 20, 10, color); circle(8, 8, 1.8f, color); circle(15, 7, 1.8f, color); circle(18, 13, 1.8f, color); circle(8, 16, 1.8f, color); }
            case "search" -> { border(3, 3, 13, 13, 7, color); line(15, 15, 21, 21, 1.6f, color); }
            case "check" -> { line(5, 12, 10, 17, 1.8f, color); line(10, 17, 19, 7, 1.8f, color); }
            case "chevron" -> { line(7, 10, 12, 15, 1.5f, color); line(12, 15, 17, 10, 1.5f, color); }
        }
        restore();
    }
}
