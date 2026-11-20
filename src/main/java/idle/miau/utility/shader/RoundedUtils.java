package idle.miau.utility.shader;

import idle.miau.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;

public class RoundedUtils {
    private static final Logger LOGGER = LogManager.getLogger(RoundedUtils.class);
    private static final Map<String, ShaderUtils> SHADERS = new HashMap<String, ShaderUtils>();
    private static final Set<String> UNAVAILABLE_SHADERS = new HashSet<String>();

    public static void drawRound(float x, float y, float width, float height, float radius, Color color) {
        drawRound(x, y, width, height, radius, false, color);
    }

    public static void drawGradientHorizontal(float x, float y, float width, float height, float radius, Color left, Color right) {
        drawGradientRound(x, y, width, height, radius, left, left, right, right);
    }

    public static void drawGradientVertical(float x, float y, float width, float height, float radius, Color top, Color bottom) {
        drawGradientRound(x, y, width, height, radius, bottom, top, bottom, top);
    }

    public static void drawGradientCornerLR(float x, float y, float width, float height, float radius, Color topLeft, Color bottomRight) {
        Color mixedColor = RenderUtils.interpolateColorC(topLeft, bottomRight, .5f);
        drawGradientRound(x, y, width, height, radius, mixedColor, topLeft, bottomRight, mixedColor);
    }

    public static void drawGradientCornerRL(float x, float y, float width, float height, float radius, Color bottomLeft, Color topRight) {
        Color mixedColor = RenderUtils.interpolateColorC(topRight, bottomLeft, .5f);
        drawGradientRound(x, y, width, height, radius, bottomLeft, mixedColor, mixedColor, topRight);
    }

    public static void drawRound(float x, float y, float width, float height, float radius, int color) {
        drawRound(x, y, width, height, radius, false, color);
    }

    public static void drawRound(float x, float y, float width, float height, float radius, boolean blur, int color) {
        ShaderUtils roundedShader = shader("roundedRect");
        if (roundedShader == null) {
            RenderUtils.drawRect(x, y, x + width, y + height, color);
            return;
        }
        RenderUtils.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        RenderUtils.setAlphaLimit(0);

        roundedShader.init();
        setupRoundedRectUniforms(x, y, width, height, radius, roundedShader);
        roundedShader.setUniformi("blur", blur ? 1 : 0);
        roundedShader.setUniformf("color", getRed(color), getGreen(color), getBlue(color), getAlpha(color));

        ShaderUtils.drawQuads(x - 1, y - 1, width + 2, height + 2);
        roundedShader.unload();
        GlStateManager.disableBlend();
    }

    public static void drawGradientRound(float x, float y, float width, float height, float radius, Color bottomLeft, Color topLeft, Color bottomRight, Color topRight) {
        ShaderUtils roundedGradientShader = shader("roundedRectGradient");
        if (roundedGradientShader == null) {
            RenderUtils.drawRect(x, y, x + width, y + height, averageColor(bottomLeft, topLeft, bottomRight, topRight));
            return;
        }
        RenderUtils.setAlphaLimit(0);
        RenderUtils.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        roundedGradientShader.init();
        setupRoundedRectUniforms(x, y, width, height, radius, roundedGradientShader);
        roundedGradientShader.setUniformf("color1", topLeft.getRed() / 255f, topLeft.getGreen() / 255f, topLeft.getBlue() / 255f, topLeft.getAlpha() / 255f);
        roundedGradientShader.setUniformf("color2", bottomLeft.getRed() / 255f, bottomLeft.getGreen() / 255f, bottomLeft.getBlue() / 255f, bottomLeft.getAlpha() / 255f);
        roundedGradientShader.setUniformf("color3", topRight.getRed() / 255f, topRight.getGreen() / 255f, topRight.getBlue() / 255f, topRight.getAlpha() / 255f);
        roundedGradientShader.setUniformf("color4", bottomRight.getRed() / 255f, bottomRight.getGreen() / 255f, bottomRight.getBlue() / 255f, bottomRight.getAlpha() / 255f);
        ShaderUtils.drawQuads(x - 1, y - 1, width + 2, height + 2);
        roundedGradientShader.unload();
        GlStateManager.disableBlend();
    }

    public static void drawGradientRound(float x, float y, float width, float height, float radius, int bottomLeft, int topLeft, int bottomRight, int topRight) {
        ShaderUtils roundedGradientShader = shader("roundedRectGradient");
        if (roundedGradientShader == null) {
            RenderUtils.drawRect(x, y, x + width, y + height, averageColor(bottomLeft, topLeft, bottomRight, topRight));
            return;
        }
        RenderUtils.setAlphaLimit(0);
        RenderUtils.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        roundedGradientShader.init();
        setupRoundedRectUniforms(x, y, width, height, radius, roundedGradientShader);

        roundedGradientShader.setUniformf("color1", getRed(topLeft), getGreen(topLeft), getBlue(topLeft), getAlpha(topLeft));
        roundedGradientShader.setUniformf("color2", getRed(bottomLeft), getGreen(bottomLeft), getBlue(bottomLeft), getAlpha(bottomLeft));
        roundedGradientShader.setUniformf("color3", getRed(topRight), getGreen(topRight), getBlue(topRight), getAlpha(topRight));
        roundedGradientShader.setUniformf("color4", getRed(bottomRight), getGreen(bottomRight), getBlue(bottomRight), getAlpha(bottomRight));

        ShaderUtils.drawQuads(x - 1, y - 1, width + 2, height + 2);
        roundedGradientShader.unload();
        GlStateManager.disableBlend();
    }

    public static void drawRound(float x, float y, float width, float height, float radius, boolean blur, Color color) {
        ShaderUtils roundedShader = shader("roundedRect");
        if (roundedShader == null) {
            RenderUtils.drawRect(x, y, x + width, y + height, color.getRGB());
            return;
        }
        RenderUtils.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        RenderUtils.setAlphaLimit(0);
        roundedShader.init();

        setupRoundedRectUniforms(x, y, width, height, radius, roundedShader);
        roundedShader.setUniformi("blur", blur ? 1 : 0);
        roundedShader.setUniformf("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);

        ShaderUtils.drawQuads(x - 1, y - 1, width + 2, height + 2);
        roundedShader.unload();
        GlStateManager.disableBlend();
    }

    public static void drawRoundOutline(float x, float y, float width, float height, float radius, float outlineThickness, Color color, Color outlineColor) {
        ShaderUtils roundedOutlineShader = shader("roundRectOutline");
        if (roundedOutlineShader == null) {
            float thickness = Math.max(1.0F, outlineThickness);
            RenderUtils.drawRect(x - thickness, y - thickness, x + width + thickness, y + height + thickness, outlineColor.getRGB());
            RenderUtils.drawRect(x, y, x + width, y + height, color.getRGB());
            return;
        }
        RenderUtils.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        RenderUtils.setAlphaLimit(0);
        roundedOutlineShader.init();

        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        setupRoundedRectUniforms(x, y, width, height, radius, roundedOutlineShader);
        roundedOutlineShader.setUniformf("outlineThickness", outlineThickness * sr.getScaleFactor());
        roundedOutlineShader.setUniformf("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
        roundedOutlineShader.setUniformf("outlineColor", outlineColor.getRed() / 255f, outlineColor.getGreen() / 255f, outlineColor.getBlue() / 255f, outlineColor.getAlpha() / 255f);

        ShaderUtils.drawQuads(x - (2 + outlineThickness), y - (2 + outlineThickness), width + (4 + outlineThickness * 2), height + (4 + outlineThickness * 2));
        roundedOutlineShader.unload();
        GlStateManager.disableBlend();
    }

    public static void drawRoundTextured(float x, float y, float width, float height, float radius, float alpha) {
        ShaderUtils roundedTexturedShader = shader("roundRectTexture");
        if (roundedTexturedShader == null) {
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            ShaderUtils.drawQuads(x, y, width, height);
            RenderUtils.resetColor();
            GlStateManager.disableBlend();
            return;
        }
        RenderUtils.resetColor();
        RenderUtils.setAlphaLimit(0);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        roundedTexturedShader.init();
        roundedTexturedShader.setUniformi("textureIn", 0);
        setupRoundedRectUniforms(x, y, width, height, radius, roundedTexturedShader);
        roundedTexturedShader.setUniformf("alpha", alpha);
        ShaderUtils.drawQuads(x - 1, y - 1, width + 2, height + 2);
        roundedTexturedShader.unload();
        GlStateManager.disableBlend();
    }

    private static void setupRoundedRectUniforms(float x, float y, float width, float height, float radius, ShaderUtils roundedTexturedShader) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        roundedTexturedShader.setUniformf("location", x * sr.getScaleFactor(),
                (Minecraft.getMinecraft().displayHeight - (height * sr.getScaleFactor())) - (y * sr.getScaleFactor()));
        roundedTexturedShader.setUniformf("rectSize", width * sr.getScaleFactor(), height * sr.getScaleFactor());
        roundedTexturedShader.setUniformf("radius", radius * sr.getScaleFactor());
    }

    public static void drawRoundedRectRise(final float x, final float y, final float width, final float height, final float radius, final int color, boolean leftTop, boolean rightTop, boolean rightBottom, boolean leftBottom) {
        ShaderUtils roundedRectRiseShader = shader("roundedRectRise");
        if (roundedRectRiseShader == null) {
            RenderUtils.drawRect(x, y, x + width, y + height, color);
            return;
        }
        GL11.glPushMatrix();
        GlStateManager.pushAttrib();
        final int programId = roundedRectRiseShader.programID;
        GL20.glUseProgram(programId);
        roundedRectRiseShader.setUniformf("u_size", width, height);
        roundedRectRiseShader.setUniformf("u_radius", radius);
        roundedRectRiseShader.setUniformf("u_color", getRed(color), getGreen(color), getBlue(color), getAlpha(color));
        roundedRectRiseShader.setUniformf("u_edges", leftTop ? 1.0F : 0.0F, rightTop ? 1.0F : 0.0F, rightBottom ? 1.0F : 0.0F, leftBottom ? 1.0F : 0.0F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        ShaderUtils.drawQuads(x, y, width, height);
        GlStateManager.disableBlend();
        GL20.glUseProgram(0);
        GlStateManager.popAttrib();
        GL11.glPopMatrix();
    }

    public static void drawRoundedRectRise(final double x, final double y, final double width, final double height, final double radius, final int color) {
        drawRoundedRectRise((float) x, (float) y, (float) width, (float) height, (float) radius, color, true, true, true, true);
    }

    private static float getRed(int color) {
        return (color >> 16 & 0xFF) / 255.0F;
    }

    private static float getGreen(int color) {
        return (color >> 8 & 0xFF) / 255.0F;
    }

    private static float getBlue(int color) {
        return (color & 0xFF) / 255.0F;
    }

    private static float getAlpha(int color) {
        return (color >> 24 & 0xFF) / 255.0F;
    }

    private static synchronized ShaderUtils shader(String name) {
        if (!ShaderCompatibility.isAvailable()) {
            return null;
        }
        ShaderUtils shader = SHADERS.get(name);
        if (shader != null || UNAVAILABLE_SHADERS.contains(name)) {
            return shader;
        }
        try {
            shader = new ShaderUtils(name);
            SHADERS.put(name, shader);
            return shader;
        }
        catch (RuntimeException | LinkageError exception) {
            UNAVAILABLE_SHADERS.add(name);
            LOGGER.warn("Shader {} is unavailable", name, exception);
            return null;
        }
    }

    private static int averageColor(Color... colors) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int alpha = 0;
        for (Color color : colors) {
            red += color.getRed();
            green += color.getGreen();
            blue += color.getBlue();
            alpha += color.getAlpha();
        }
        int count = colors.length;
        return new Color(red / count, green / count, blue / count, alpha / count).getRGB();
    }

    private static int averageColor(int... colors) {
        Color[] values = new Color[colors.length];
        for (int index = 0; index < colors.length; index++) {
            values[index] = new Color(colors[index], true);
        }
        return averageColor(values);
    }
}
