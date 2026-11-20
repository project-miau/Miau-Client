package idle.miau.clickgui.modern;

import idle.miau.clickgui.panel.GlStateScope;
import idle.miau.module.impl.client.ClickGui;
import idle.miau.module.impl.render.ArrayListModule;
import idle.miau.utility.shader.ShaderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL2.*;

final class MiauRenderer {
    static final String MEDIUM = "productsans-medium";
    static final String BOLD = "productsans-bold";
    static final String ICON = "materialicons-regular";
    static final String OUTLINED = "materialicons-outlined";
    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<ByteBuffer> fonts = new ArrayList<>();
    private final NVGColor first = NVGColor.create();
    private final NVGColor second = NVGColor.create();
    private final NVGPaint paint = NVGPaint.create();
    private final NVGPaint background = NVGPaint.create();
    private long vg;
    private Framebuffer blurA;
    private Framebuffer blurB;
    private ShaderUtils blur;
    private int backgroundImage;
    private int stencilBuffer = -1;
    private int stencilWidth;
    private int stencilHeight;
    private GlStateScope state;
    private int previousProgram;
    private int previousArray;
    private int previousElements;
    private int previousFramebuffer;
    private int previousRenderbuffer;
    private int previousVertexArray;
    private boolean frame;
    private float layerAlpha = 1;
    private float layerX;
    private float layerY;
    private float frameWidth;
    private float frameHeight;

    void begin(float width, float height) {
        state = GlStateScope.open();
        previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        previousArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        previousElements = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try {
            initialize();
            prepareBackground(width, height);
            mc.getFramebuffer().bindFramebuffer(true);
            prepareStencil();
            GL11.glStencilMask(0xff);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL20.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            nvgBeginFrame(vg, width, height, mc.displayWidth / width);
            frame = true;
            layerAlpha = 1;
            layerX = layerY = 0;
            frameWidth = width;
            frameHeight = height;
        } catch (RuntimeException | Error exception) {
            end();
            throw exception;
        }
    }

    void end() {
        try {
            if (frame) {
                nvgEndFrame(vg);
            }
        } finally {
            frame = false;
            if (state != null) {
                GL20.glUseProgram(previousProgram);
                GL30.glBindVertexArray(previousVertexArray);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArray);
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, previousElements);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
                state.close();
                state = null;
            }
        }
    }

    private void initialize() {
        if (vg != 0) {
            return;
        }
        vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (vg == 0) {
            throw new IllegalStateException("Cannot initialize Miau renderer");
        }
        for (String font : new String[]{MEDIUM, BOLD, ICON, OUTLINED}) {
            try (InputStream stream = MiauRenderer.class.getResourceAsStream("/assets/miau/fonts/clickgui/" + font + ".ttf")) {
                if (stream == null) {
                    throw new IOException("Missing font " + font);
                }
                byte[] bytes = stream.readAllBytes();
                ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
                data.put(bytes).flip();
                fonts.add(data);
                if (nvgCreateFontMem(vg, font, data, false) < 0) {
                    throw new IOException("Cannot load font " + font);
                }
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
        blur = new ShaderUtils("miau:shaders/clickgui/blur.frag", "miau:shaders/clickgui/blur.vert");
    }

    private void prepareStencil() {
        Framebuffer target = mc.getFramebuffer();
        if (!OpenGlHelper.isFramebufferEnabled() || target.depthBuffer < 0) {
            return;
        }
        if (target.depthBuffer == stencilBuffer && target.framebufferWidth == stencilWidth
                && target.framebufferHeight == stencilHeight) {
            return;
        }
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, target.depthBuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8,
                target.framebufferWidth, target.framebufferHeight);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER, target.depthBuffer);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER, target.depthBuffer);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete Miau framebuffer");
        }
        stencilBuffer = target.depthBuffer;
        stencilWidth = target.framebufferWidth;
        stencilHeight = target.framebufferHeight;
    }

    private void prepareBackground(float width, float height) {
        if (!OpenGlHelper.isFramebufferEnabled()) {
            releaseBackground();
            return;
        }
        if (blurA == null || blurA.framebufferWidth != mc.displayWidth || blurA.framebufferHeight != mc.displayHeight) {
            releaseBackground();
            blurA = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            blurB = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            blurA.setFramebufferFilter(GL11.GL_LINEAR);
            blurB.setFramebufferFilter(GL11.GL_LINEAR);
            backgroundImage = nvglCreateImageFromHandle(vg, blurB.framebufferTexture, mc.displayWidth,
                    mc.displayHeight, NVG_IMAGE_FLIPY | NVG_IMAGE_NODELETE);
        }
        int texture = mc.getFramebuffer().framebufferTexture;
        float radius = ClickGui.miauBlurRadius == null ? 7 : (float) ClickGui.miauBlurRadius.getInput();
        if (ClickGui.miauBlur != null && !ClickGui.miauBlur.isToggled()) {
            radius = 0;
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        for (int pass = 0; pass < 6; pass++) {
            Framebuffer destination = pass % 2 == 0 ? blurA : blurB;
            destination.bindFramebuffer(true);
            blur.init();
            blur.setUniformi("image", 0);
            blur.setUniformf("stepSize", pass % 2 == 0 ? 1.0f / mc.displayWidth : 0,
                    pass % 2 == 0 ? 0 : 1.0f / mc.displayHeight);
            blur.setUniformf("radius", radius / (1 << (pass / 2)));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(-1, -1);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, -1);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(-1, 1);
            GL11.glEnd();
            texture = destination.framebufferTexture;
        }
        blur.unload();
    }

    void releaseBackground() {
        if (backgroundImage != 0) {
            nvgDeleteImage(vg, backgroundImage);
            backgroundImage = 0;
        }
        if (blurA != null) {
            blurA.deleteFramebuffer();
            blurB.deleteFramebuffer();
            blurA = blurB = null;
        }
    }

    void clip(float x, float y, float width, float height, Runnable draw) {
        nvgSave(vg);
        nvgIntersectScissor(vg, x, y, Math.max(0, width), Math.max(0, height));
        try {
            draw.run();
        } finally {
            nvgRestore(vg);
        }
    }

    void alpha(float alpha) {
        nvgGlobalAlpha(vg, layerAlpha * alpha);
    }

    void layer(float offsetX, float offsetY, float alpha, Runnable draw) {
        float previousAlpha = layerAlpha;
        float previousX = layerX;
        float previousY = layerY;
        nvgSave(vg);
        nvgTranslate(vg, offsetX, offsetY);
        layerAlpha *= alpha;
        layerX += offsetX;
        layerY += offsetY;
        alpha(1);
        try {
            draw.run();
        } finally {
            layerAlpha = previousAlpha;
            layerX = previousX;
            layerY = previousY;
            nvgRestore(vg);
        }
    }

    void rect(float x, float y, float width, float height, float radius, int color) {
        varying(x, y, width, height, radius, radius, color);
    }

    void varying(float x, float y, float width, float height, float top, float bottom, int color) {
        path(x, y, width, height, top, bottom);
        nvgFillColor(vg, color(first, color));
        nvgFill(vg);
    }

    void glass(float x, float y, float width, float height, float top, float bottom) {
        if (backgroundImage == 0) {
            return;
        }
        path(x, y, width, height, top, bottom);
        nvgImagePattern(vg, -layerX, -layerY, frameWidth, frameHeight, 0, backgroundImage, 1, background);
        nvgFillPaint(vg, background);
        nvgFill(vg);
    }

    void gradient(float x, float y, float width, float height, float top, float bottom,
                  int from, int to, boolean vertical) {
        path(x, y, width, height, top, bottom);
        nvgLinearGradient(vg, x, y, vertical ? x : x + width, vertical ? y + height : y,
                color(first, from), color(second, to), paint);
        nvgFillPaint(vg, paint);
        nvgFill(vg);
    }

    void outline(float x, float y, float width, float height, float radius, float thickness, int color) {
        path(x, y, width, height, radius, radius);
        nvgStrokeColor(vg, color(first, color));
        nvgStrokeWidth(vg, thickness);
        nvgStroke(vg);
    }

    void text(String font, String text, float x, float baseline, float size, int color) {
        nvgFontFace(vg, font);
        nvgFontSize(vg, size);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_BASELINE);
        nvgFillColor(vg, color(first, color));
        nvgText(vg, x, baseline, text);
    }

    float width(String font, String text, float size) {
        nvgFontFace(vg, font);
        nvgFontSize(vg, size);
        return nvgTextBounds(vg, 0, 0, text, (float[]) null);
    }

    String trim(String font, String text, float size, float maximum) {
        if (width(font, text, size) <= maximum) {
            return text;
        }
        int end = text.length();
        while (end > 0 && width(font, text.substring(0, end) + "…", size) > maximum) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    void chevron(float x, float y, float size, float progress, int color) {
        nvgSave(vg);
        nvgTranslate(vg, x, y);
        nvgRotate(vg, progress * (float) Math.PI);
        nvgFontFace(vg, ICON);
        nvgFontSize(vg, size);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, color(first, color));
        nvgText(vg, 0, 0, "\ue5cf");
        nvgRestore(vg);
    }

    private void path(float x, float y, float width, float height, float top, float bottom) {
        nvgBeginPath(vg);
        nvgRoundedRectVarying(vg, x, y, Math.max(0, width), Math.max(0, height), top, top, bottom, bottom);
    }

    private static NVGColor color(NVGColor target, int argb) {
        return target.r((argb >> 16 & 255) / 255f).g((argb >> 8 & 255) / 255f)
                .b((argb & 255) / 255f).a((argb >>> 24) / 255f);
    }

    static int opacity(int color, float alpha) {
        return (color & 0xffffff) | Math.round(Math.max(0, Math.min(1, alpha)) * (color >>> 24)) << 24;
    }

    static int darker(int color, float amount) {
        float factor = 1 - amount;
        return color & 0xff000000 | (int) ((color >> 16 & 255) * factor) << 16
                | (int) ((color >> 8 & 255) * factor) << 8 | (int) ((color & 255) * factor);
    }

    static int primary() {
        return ArrayListModule.getHudPrimaryColor(0);
    }

    static int secondary() {
        return ArrayListModule.getHudSecondaryColor(0);
    }
}
