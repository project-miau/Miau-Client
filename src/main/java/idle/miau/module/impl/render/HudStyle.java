package idle.miau.module.impl.render;

import idle.miau.utility.IMinecraftInstance;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.font.FontManager;
import idle.miau.utility.font.MinecraftFontAdapter;
import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.utility.shader.BlurUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.List;

public final class HudStyle implements IMinecraftInstance {
    private static final ModernHudSettings DEFAULTS = new ModernHudSettings();
    private static float opacity = 1;
    private static boolean effectMask;
    private static long frameTime;
    private static boolean localUnits;

    private HudStyle() {
    }

    static ModernHudSettings settings() {
        ModernHudSettings settings = ArrayListModule.getAppearanceSettings();
        return settings == null ? DEFAULTS : settings;
    }

    public static MiauFontRenderer font() {
        return font(1);
    }

    public static MiauFontRenderer font(float size) {
        return font(ArrayListModule.getSelectedFontName(), scale() * size);
    }

    public static MiauFontRenderer font(String family, float size) {
        return new StyledFont(FontManager.getHudRenderer(family, size));
    }

    public static MiauFontRenderer minecraftFont(FontRenderer renderer) {
        return new StyledFont(new MinecraftFontAdapter(renderer, 1.0f, false));
    }

    public static MiauFontRenderer semanticFont() {
        return font();
    }

    public static MiauFontRenderer themedFont() {
        return new StyledFont(FontManager.getHudRenderer(ArrayListModule.getSelectedFontName(), scale()), true);
    }

    public static float scale() {
        return ArrayListModule.getSelectedFontScale();
    }

    public static void localUnits(Runnable draw) {
        boolean previous = localUnits;
        localUnits = true;
        try {
            draw.run();
        } finally {
            localUnits = previous;
        }
    }

    private static float geometryScale() {
        return localUnits ? 1 : scale();
    }

    public static float textOffsetX() {
        return (float) settings().textOffsetX.getInput() * geometryScale();
    }

    public static float textOffsetY() {
        return (float) settings().textOffsetY.getInput() * geometryScale();
    }

    public static float radius() {
        return 4 * geometryScale();
    }

    public static float paddingX() {
        return (float) settings().horizontalPadding.getInput() * geometryScale();
    }

    public static float paddingY() {
        return (float) settings().verticalPadding.getInput() * geometryScale();
    }

    public static boolean blurEnabled() {
        return settings().blurEnabled();
    }

    public static boolean shaderEnabled() {
        return settings().shaderEnabled();
    }

    public static float blurRadius() {
        return (float) settings().blurRadius.getInput();
    }

    public static float shaderRadius() {
        return (float) settings().effectRadius.getInput();
    }

    public static int accent(float x, float y) {
        return ArrayListModule.getHudColorAtPosition(x, y, time());
    }

    public record Panel(float x, float y, float width, float height, float alpha, Runnable text, Runnable foreground) {
        public Panel(float x, float y, float width, float height, Runnable text) {
            this(x, y, width, height, 1, text, null);
        }
    }

    public record PanelStyle(float radius, int backgroundColor, boolean blur, int blurPasses, float blurRadius,
                             boolean bloom, int bloomPasses, float bloomRadius, int bloomColor) {
        public static PanelStyle shared(float radius, int backgroundColor, boolean blur, boolean bloom) {
            ModernHudSettings settings = settings();
            int effectAlpha = Math.round((float) settings.effectOpacity.getInput());
            int effectColor = settings.effect.getInput() == 2 ? 0xFFFFFFFF : 0xFF000000;
            return new PanelStyle(radius, backgroundColor, blur, (int) settings.blurPasses.getInput(), HudStyle.blurRadius(),
                    bloom, (int) settings.effectPasses.getInput(), HudStyle.shaderRadius(), Utils.mergeAlpha(effectColor, effectAlpha));
        }

        public static PanelStyle local(float radius, int backgroundColor, boolean blur, float blurRadius,
                                       boolean bloom, float bloomRadius) {
            return new PanelStyle(radius, backgroundColor, blur, 2, blurRadius, bloom, 2, bloomRadius, 0xFFFFFFFF);
        }
    }

    public static PanelStyle sharedPanelStyle() {
        return PanelStyle.shared(radius(), 0x6E000000, blurEnabled(), shaderEnabled());
    }

    public static void panel(float x, float y, float width, float height, Runnable text) {
        panel(x, y, width, height, 1, text);
    }

    public static void panel(float x, float y, float width, float height, float alpha, Runnable text) {
        panel(x, y, width, height, alpha, text, null);
    }

    public static void panel(float x, float y, float width, float height, float alpha, Runnable text, Runnable foreground) {
        panels(List.of(new Panel(x, y, width, height, alpha, text, foreground)));
    }

    public static void panelWithoutShaders(float x, float y, float width, float height, Runnable text) {
        panelWithoutShaders(x, y, width, height, 1, text);
    }

    public static void panelWithoutShaders(float x, float y, float width, float height, float alpha, Runnable text) {
        panel(new Panel(x, y, width, height, alpha, text, null),
                PanelStyle.local(radius(), 0x6E000000, false, 0, false, 0));
    }

    public static void panels(List<Panel> panels) {
        panels(panels, sharedPanelStyle());
    }

    public static void panel(Panel panel, PanelStyle style) {
        panels(List.of(panel), style);
    }

    public static void panels(List<Panel> panels, PanelStyle style) {
        renderPanels(panels, style, false);
    }

    static void arrayListPanels(List<Panel> panels) {
        renderPanels(panels, null, true);
    }

    private static void renderPanels(List<Panel> panels, PanelStyle style, boolean arrayList) {
        if (panels.isEmpty()) return;
        ModernHudSettings settings = settings();
        float previousOpacity = opacity;
        boolean previousMask = effectMask;
        long previousTime = frameTime;
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        frameTime = System.currentTimeMillis();
        try {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            if (arrayList ? blurEnabled() : style.blur) {
                BlurUtils.renderBlur(() -> {
                    if (arrayList) drawPanels(panels, previousOpacity, true);
                    else drawPlainPanels(panels, previousOpacity, style, PanelPass.BLUR);
                }, arrayList ? (int) settings.blurPasses.getInput() : style.blurPasses,
                        arrayList ? blurRadius() : style.blurRadius);
            }
            if (arrayList && shaderEnabled()) {
                BlurUtils.renderBloom(() -> {
                    effectMask = true;
                    GlStateManager.pushMatrix();
                    try {
                        GlStateManager.translate(settings.effectOffsetX.getInput() * geometryScale(),
                                settings.effectOffsetY.getInput() * geometryScale(), 0);
                        drawPanels(panels, previousOpacity, false);
                    } finally {
                        GlStateManager.popMatrix();
                        effectMask = previousMask;
                    }
                }, (int) settings.effectPasses.getInput(), shaderRadius());
            } else if (!arrayList && style.bloom) {
                BlurUtils.renderBloom(() -> drawPlainPanels(panels, previousOpacity, style, PanelPass.BLOOM),
                        style.bloomPasses, style.bloomRadius);
            }
            effectMask = false;
            if (arrayList) drawPanels(panels, previousOpacity, false);
            else drawPlainPanels(panels, previousOpacity, style, PanelPass.CONTENT);
        } finally {
            opacity = previousOpacity;
            effectMask = previousMask;
            frameTime = previousTime;
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.enableTexture2D();
            GlStateManager.depthMask(depthWrite);
            if (depthEnabled) GlStateManager.enableDepth();
            else GlStateManager.disableDepth();
            RenderUtils.setAlphaLimit(10);
            RenderUtils.resetColor();
        }
    }

    private enum PanelPass {
        CONTENT, BLUR, BLOOM
    }

    private static void drawPlainPanels(List<Panel> panels, float parentOpacity, PanelStyle style, PanelPass pass) {
        for (Panel panel : panels) {
            if (panel.width <= 0 || panel.height <= 0 || panel.alpha <= 0) continue;
            opacity = parentOpacity * Math.min(1, panel.alpha);
            int color = pass == PanelPass.BLUR ? 0xFFFFFFFF
                    : pass == PanelPass.BLOOM ? style.bloomColor : style.backgroundColor;
            int alpha = Math.round((color >>> 24) * opacity);
            if (alpha > 0) {
                float rounding = Math.min(style.radius, Math.min(panel.width, panel.height) / 2);
                RenderUtils.drawRoundedRectangle(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height,
                        rounding, Utils.mergeAlpha(color, alpha));
            }
            if (pass == PanelPass.CONTENT) {
                if (panel.foreground != null) panel.foreground.run();
                if (panel.text != null) panel.text.run();
            }
        }
    }

    private static void drawPanels(List<Panel> panels, float parentOpacity, boolean blurMask) {
        ModernHudSettings settings = settings();
        int target = (int) settings.effectTarget.getInput();
        for (Panel panel : panels) {
            if (panel.width <= 0 || panel.height <= 0 || panel.alpha <= 0) continue;
            opacity = parentOpacity * Math.min(1, panel.alpha);
            if (blurMask) {
                background(panel.x, panel.y, panel.width, panel.height, true);
                continue;
            }
            if (effectMask) {
                if (target == 0 || target == 3) background(panel.x, panel.y, panel.width, panel.height, false);
                if (target == 2 || target == 3) outline(panel.x, panel.y, panel.width, panel.height);
                if ((target == 1 || target == 3) && panel.text != null) panel.text.run();
            } else {
                if (settings.background.getInput() != 0) background(panel.x, panel.y, panel.width, panel.height, false);
                outline(panel.x, panel.y, panel.width, panel.height);
                if (panel.foreground != null) panel.foreground.run();
                if (panel.text != null) panel.text.run();
            }
        }
    }

    private static void background(float x, float y, float width, float height, boolean blurMask) {
        ModernHudSettings settings = settings();
        int alpha = Math.round((blurMask ? 255 : effectMask ? (float) settings.effectOpacity.getInput()
                : (float) settings.backgroundAlpha.getInput()) * opacity);
        if (alpha <= 0) return;
        int first = settings.backgroundColor.getColor();
        int theme = accent(x, y);
        if (blurMask) first = 0xFFFFFFFF;
        else if (effectMask) first = effectColor(theme, theme);
        else if (settings.background.getInput() == 2
                || settings.background.getInput() == 3 && settings.backgroundGradient.getInput() == 0) {
            first = ModernArrayListRenderer.mix(first, theme, (float) settings.backgroundTint.getInput() / 100);
        }
        first = Utils.mergeAlpha(first, alpha);
        float rounding = Math.min((float) settings.rounding.getInput() * geometryScale(), Math.min(width, height) / 2);
        if (!blurMask && !effectMask && settings.background.getInput() == 3) {
            int second = settings.backgroundGradient.getInput() == 1 ? settings.backgroundColor2.getColor()
                    : ModernArrayListRenderer.mix(settings.backgroundColor.getColor(), accent(x + width, y + height + 40),
                    (float) settings.backgroundTint.getInput() / 100);
            int[] corners = ModernArrayListRenderer.gradientCorners(first, Utils.mergeAlpha(second, alpha),
                    (int) settings.gradientDirection.getInput());
            RenderUtils.drawRoundedGradientRect(x, y, x + width, y + height, rounding,
                    corners[0], corners[1], corners[2], corners[3]);
        } else {
            RenderUtils.drawRoundedRectangle(x, y, x + width, y + height, rounding, first);
        }
    }

    private static void outline(float x, float y, float width, float height) {
        ModernHudSettings settings = settings();
        int mode = (int) settings.outline.getInput();
        int alpha = Math.round((effectMask ? (float) settings.effectOpacity.getInput()
                : (float) settings.outlineOpacity.getInput()) * opacity);
        if (mode == 0 || alpha <= 0) return;
        float thickness = Math.min((float) settings.outlineWidth.getInput() * geometryScale(), Math.min(width, height) / 2);
        int theme = accent(x, y);
        int color = settings.outlineColorMode.getInput() == 0 ? theme
                : settings.outlineColorMode.getInput() == 1 ? 0xFFFFFFFF : settings.outlineColor.getColor();
        color = Utils.mergeAlpha(effectMask ? effectColor(color, theme) : color, alpha);
        if (mode == 2 || mode == 3) {
            float rounding = Math.min((float) settings.rounding.getInput() * geometryScale(), Math.min(width, height) / 2);
            roundedOutline(x, y, width, height, rounding, thickness, color);
        } else if (mode == 4 || mode == 5) {
            float top = mode == 4 ? y : y + height - thickness;
            RenderUtils.drawRect(x, top, x + width, top + thickness, color);
        } else {
            int side = (int) settings.sidebar.getInput();
            if (side == 0 || side == 2) RenderUtils.drawRect(x + width - thickness, y, x + width, y + height, color);
            if (side == 1 || side == 2) RenderUtils.drawRect(x, y, x + thickness, y + height, color);
        }
    }

    private static void roundedOutline(float x, float y, float width, float height,
                                       float radius, float thickness, int color) {
        if (radius <= 0) {
            RenderUtils.drawRect(x, y, x + width, y + thickness, color);
            RenderUtils.drawRect(x, y + height - thickness, x + width, y + height, color);
            RenderUtils.drawRect(x, y + thickness, x + thickness, y + height - thickness, color);
            RenderUtils.drawRect(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                    (color & 255) / 255f, (color >>> 24) / 255f);
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            float innerRadius = Math.max(0, radius - thickness);
            for (int corner = 0; corner <= 4; corner++) {
                int index = corner % 4;
                float centerX = x + (index == 0 || index == 3 ? radius : width - radius);
                float centerY = y + (index < 2 ? radius : height - radius);
                for (int step = 0; step <= 16; step++) {
                    double angle = Math.toRadians(180 + index * 90 + step * 90f / 16);
                    float cos = (float) Math.cos(angle);
                    float sin = (float) Math.sin(angle);
                    GL11.glVertex2f(centerX + cos * radius, centerY + sin * radius);
                    GL11.glVertex2f(centerX + cos * innerRadius, centerY + sin * innerRadius);
                }
            }
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static int effectColor(int source, int theme) {
        ModernHudSettings settings = settings();
        if (settings.effectColorMode.getInput() == 2) return settings.effectColor.getColor();
        if (settings.effectColorMode.getInput() == 1) return theme;
        return settings.effect.getInput() == 1 ? 0xFF000000 : source;
    }

    private static long time() {
        return frameTime == 0 ? System.currentTimeMillis() : frameTime;
    }

    private static final class StyledFont implements MiauFontRenderer {
        private final MiauFontRenderer delegate;
        private final boolean themedColors;

        private StyledFont(MiauFontRenderer delegate) {
            this(delegate, false);
        }

        private StyledFont(MiauFontRenderer delegate, boolean themedColors) {
            this.delegate = delegate;
            this.themedColors = themedColors;
        }

        @Override
        public int drawString(String text, float x, float y, int color, boolean shadow) {
            int alpha = color >>> 24;
            if (alpha == 0) alpha = 255;
            int sourceAlpha = alpha;
            return drawGlyphString(text, x, y, (character, offset, width, formatting) ->
                    Utils.mergeAlpha(formatting != null ? formatting : color, sourceAlpha), shadow);
        }

        @Override
        public int drawGlyphString(String text, float x, float y, GlyphColorProvider provider, boolean shadow) {
            ModernHudSettings settings = settings();
            float textAlpha = (float) settings.textOpacity.getInput() / 255 * opacity;
            if (effectMask) textAlpha *= (float) settings.effectOpacity.getInput() / 255;
            if (textAlpha <= 0) return Math.round(x) + getStringWidth(text);
            float factor = textAlpha;
            float drawX = x + textOffsetX();
            float drawY = y + textOffsetY();
            return delegate.drawGlyphString(text, drawX, drawY, (character, offset, width, formatting) -> {
                int source = provider.colorForGlyph(character, offset, width, formatting);
                int theme = themedColors || effectMask
                        ? accent(settings.gradientText.isToggled() ? drawX + offset : drawX, drawY) : source;
                int rgb = formatting != null ? formatting
                        : !themedColors ? source
                        : settings.textColorMode.getInput() == 0 ? theme
                        : settings.textColorMode.getInput() == 1 ? 0xFFFFFFFF : settings.textColor.getColor();
                int alpha = Math.round((source >>> 24) * factor);
                return Utils.mergeAlpha(effectMask ? effectColor(rgb, theme) : rgb, alpha);
            }, !effectMask && ArrayListModule.isHudTextShadowEnabled());
        }

        @Override
        public int getStringWidth(String text) {
            return delegate.getStringWidth(text);
        }

        @Override
        public int getFontHeight() {
            return delegate.getFontHeight();
        }

        @Override
        public int getLineHeight() {
            return delegate.getLineHeight();
        }

        @Override
        public float getLineHeightExact() {
            return delegate.getLineHeightExact();
        }

        @Override
        public int getTextTopOffset() {
            return delegate.getTextTopOffset();
        }

        @Override
        public int getTextBottomOffset() {
            return delegate.getTextBottomOffset();
        }
    }
}
